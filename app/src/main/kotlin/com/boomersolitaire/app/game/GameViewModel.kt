package com.boomersolitaire.app.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boomersolitaire.app.data.GameRecord
import com.boomersolitaire.app.data.GameRecordDao
import com.boomersolitaire.app.data.ModeStats
import com.boomersolitaire.app.data.SaveRepository
import com.boomersolitaire.app.data.SavedGame
import com.boomersolitaire.app.data.Settings
import com.boomersolitaire.app.data.SettingsRepository
import com.boomersolitaire.app.data.computeModeStats
import com.boomersolitaire.engine.AutoComplete
import com.boomersolitaire.engine.Card
import com.boomersolitaire.engine.Game
import com.boomersolitaire.engine.GameState
import com.boomersolitaire.engine.Hints
import com.boomersolitaire.engine.Move
import com.boomersolitaire.engine.Rules
import com.boomersolitaire.engine.Suit
import com.boomersolitaire.engine.Taps
import com.boomersolitaire.engine.WinnableDealer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Where a hint points: cards to pulse and the destination to pulse. */
data class HintHighlight(
    val cardIds: List<Int>,
    val destination: HintDestination?,
    val message: HintMessage,
)

sealed class HintDestination {
    data class TableauColumn(val column: Int) : HintDestination()
    data class Foundation(val suit: Suit) : HintDestination()
    data object Stock : HintDestination()
}

enum class HintMessage { NONE, TRY_DRAWING, NO_MOVES }

/** One-shot invalid-move feedback; nonce distinguishes repeats. */
data class ShakeEvent(val cardIds: List<Int>, val nonce: Long)

data class WinSummary(
    val durationMs: Long,
    val moves: Int,
    val streak: Int,
    val isFastestWin: Boolean,
    val isFewestMoves: Boolean,
    val isBestStreak: Boolean,
    val dayStreak: Long,
)

enum class GameSound { PLACE, FLIP, SLIDE, SHUFFLE, WIN }

data class GameUiState(
    val state: GameState? = null,
    val canUndo: Boolean = false,
    val moveCount: Int = 0,
    val elapsedMs: Long = 0,
    val isWon: Boolean = false,
    val canAutoComplete: Boolean = false,
    val isAutoCompleting: Boolean = false,
    val hint: HintHighlight? = null,
    val shake: ShakeEvent? = null,
    val isDealing: Boolean = false,
    val winSummary: WinSummary? = null,
    val settings: Settings = Settings(),
)

class GameViewModel(
    private val settingsRepo: SettingsRepository,
    private val saveRepo: SaveRepository,
    private val statsDao: GameRecordDao,
) : ViewModel() {

    private val _ui = MutableStateFlow(GameUiState())
    val ui: StateFlow<GameUiState> = _ui.asStateFlow()

    private val _sounds = MutableSharedFlow<GameSound>(extraBufferCapacity = 8)
    val sounds: SharedFlow<GameSound> = _sounds.asSharedFlow()

    private var game: Game? = null
    private var startedAtEpochMs: Long = 0
    private var accumulatedMs: Long = 0
    private var resumedAtMs: Long? = null
    private var provenWinnable = false
    private var recorded = false
    private var shakeNonce = 0L
    private var hintJob: Job? = null
    private var autoCompleteJob: Job? = null

    init {
        viewModelScope.launch { statsDao.purgeImpossibleDurations() }
        viewModelScope.launch {
            settingsRepo.settings.collect { s ->
                _ui.value = _ui.value.copy(settings = s)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (resumedAtMs != null && game != null && !_ui.value.isWon) {
                    _ui.value = _ui.value.copy(elapsedMs = currentElapsed())
                }
            }
        }
    }

    private fun currentElapsed(): Long =
        accumulatedMs + (resumedAtMs?.let { System.currentTimeMillis() - it } ?: 0L)

    // ---- Lifecycle from the game screen ----

    fun onScreenResumed() {
        if (resumedAtMs == null) resumedAtMs = System.currentTimeMillis()
        viewModelScope.launch {
            settingsRepo.recordPlayedToday(LocalDate.now().toEpochDay())
        }
    }

    fun onScreenPaused() {
        resumedAtMs?.let { accumulatedMs += System.currentTimeMillis() - it }
        resumedAtMs = null
        persist()
    }

    // ---- Game setup ----

    /** Resume the saved game if one exists, else start a new one. */
    fun resumeOrNew() {
        if (game != null) return
        viewModelScope.launch {
            val saved = saveRepo.savedGame.first()
            if (saved != null && !Game.restore(saved.initial, saved.moves).state.isWon) {
                val restored = Game.restore(saved.initial, saved.moves)
                game = restored
                startedAtEpochMs = saved.startedAtEpochMs
                accumulatedMs = saved.elapsedMs
                provenWinnable = saved.provenWinnable
                recorded = false
                publish(dealing = false)
            } else {
                newGame()
            }
        }
    }

    fun newGame() {
        autoCompleteJob?.cancel()
        hintJob?.cancel()
        viewModelScope.launch {
            recordAbandonIfNeeded()
            val settings = _ui.value.settings
            _ui.value = _ui.value.copy(state = null, isDealing = true, winSummary = null, isWon = false, hint = null)
            val drawCount = if (settings.drawThree) 3 else 1
            val deal = withContext(Dispatchers.Default) {
                if (settings.winnableDeals) {
                    WinnableDealer.winnableDeal(drawCount, maxNodesPerAttempt = 60_000)
                } else {
                    WinnableDealer.randomDeal(drawCount)
                }
            }
            game = Game(deal.state)
            provenWinnable = deal.provenWinnable
            startedAtEpochMs = System.currentTimeMillis()
            accumulatedMs = 0
            // Restart the clock here, not in the screen-resume hook: "Play
            // again" never leaves the game screen, and a win nulls the clock —
            // without this, every replayed game recorded a 0:00 duration.
            resumedAtMs = System.currentTimeMillis()
            recorded = false
            _sounds.tryEmit(GameSound.SHUFFLE)
            publish(dealing = true)
            persist()
        }
    }

    /** Called by the board when the deal animation has finished. */
    fun onDealAnimationDone() {
        if (_ui.value.isDealing) _ui.value = _ui.value.copy(isDealing = false)
    }

    // ---- Moves ----

    fun onTapStock() {
        val g = game ?: return
        val move = if (g.state.stock.isNotEmpty()) Move.Draw else Move.Recycle
        if (g.play(move) != null) {
            _sounds.tryEmit(if (move == Move.Recycle) GameSound.SHUFFLE else GameSound.FLIP)
            afterMove()
        }
    }

    fun onTapWaste() = tapMove(Taps.Source.Waste) { it.state.wasteTop?.let { c -> listOf(c.id) } ?: emptyList() }

    fun onTapTableau(column: Int, cardIndex: Int) =
        tapMove(Taps.Source.Tableau(column, cardIndex)) { g ->
            val col = g.state.tableau.getOrNull(column) ?: return@tapMove emptyList()
            if (cardIndex >= col.faceDownCount && cardIndex < col.cards.size) {
                col.cards.subList(cardIndex, col.cards.size).map(Card::id)
            } else emptyList()
        }

    fun onTapFoundation(suit: Suit) =
        tapMove(Taps.Source.Foundation(suit)) { g ->
            g.state.foundationTop(suit)?.let { listOf(it.id) } ?: emptyList()
        }

    private inline fun tapMove(source: Taps.Source, shakeIds: (Game) -> List<Int>) {
        val g = game ?: return
        val move = Taps.bestMove(g.state, source)
        if (move != null && g.play(move) != null) {
            _sounds.tryEmit(GameSound.PLACE)
            afterMove()
        } else {
            val ids = shakeIds(g)
            if (ids.isNotEmpty()) {
                _ui.value = _ui.value.copy(shake = ShakeEvent(ids, ++shakeNonce))
            }
        }
    }

    /** Drag-and-drop: attempt an explicit move resolved by the UI. */
    fun requestMove(move: Move, shakeCardIds: List<Int>) {
        val g = game ?: return
        if (g.play(move) != null) {
            _sounds.tryEmit(GameSound.PLACE)
            afterMove()
        } else if (shakeCardIds.isNotEmpty()) {
            _ui.value = _ui.value.copy(shake = ShakeEvent(shakeCardIds, ++shakeNonce))
        }
    }

    fun undo() {
        autoCompleteJob?.cancel()
        val g = game ?: return
        if (g.undo() != null) {
            _sounds.tryEmit(GameSound.SLIDE)
            _ui.value = _ui.value.copy(winSummary = null)
            afterMove(countAsMove = false)
        }
    }

    fun hint() {
        val g = game ?: return
        val highlight = when (val h = Hints.hint(g.state)) {
            is Hints.Hint.Suggestion -> hintHighlight(g.state, h.move)
            is Hints.Hint.DrawFromStock -> HintHighlight(emptyList(), HintDestination.Stock, HintMessage.TRY_DRAWING)
            is Hints.Hint.NoMoves -> HintHighlight(emptyList(), null, HintMessage.NO_MOVES)
        }
        _ui.value = _ui.value.copy(hint = highlight)
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(4000)
            _ui.value = _ui.value.copy(hint = null)
        }
    }

    private fun hintHighlight(state: GameState, move: Move): HintHighlight = when (move) {
        is Move.WasteToFoundation ->
            HintHighlight(listOfNotNull(state.wasteTop?.id), HintDestination.Foundation(move.suit), HintMessage.NONE)
        is Move.WasteToTableau ->
            HintHighlight(listOfNotNull(state.wasteTop?.id), HintDestination.TableauColumn(move.toColumn), HintMessage.NONE)
        is Move.TableauToFoundation -> {
            val card = state.tableau[move.fromColumn].topCard
            HintHighlight(listOfNotNull(card?.id), card?.let { HintDestination.Foundation(it.suit) }, HintMessage.NONE)
        }
        is Move.TableauToTableau -> {
            val col = state.tableau[move.fromColumn]
            HintHighlight(
                col.cards.subList(move.cardIndex, col.cards.size).map(Card::id),
                HintDestination.TableauColumn(move.toColumn),
                HintMessage.NONE,
            )
        }
        is Move.FoundationToTableau ->
            HintHighlight(
                listOfNotNull(state.foundationTop(move.suit)?.id),
                HintDestination.TableauColumn(move.toColumn),
                HintMessage.NONE,
            )
        else -> HintHighlight(emptyList(), HintDestination.Stock, HintMessage.TRY_DRAWING)
    }

    fun autoComplete() {
        val g = game ?: return
        if (!AutoComplete.canAutoComplete(g.state)) return
        autoCompleteJob?.cancel()
        autoCompleteJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(isAutoCompleting = true)
            for (move in AutoComplete.autoCompleteMoves(g.state)) {
                if (g.play(move) == null) break
                _sounds.tryEmit(GameSound.PLACE)
                publish(dealing = false)
                delay(if (_ui.value.settings.reduceMotion) 60L else 160L)
            }
            _ui.value = _ui.value.copy(isAutoCompleting = false)
            afterMove()
        }
    }

    // ---- Bookkeeping ----

    private fun afterMove(countAsMove: Boolean = true) {
        _ui.value = _ui.value.copy(hint = null)
        publish(dealing = false)
        persist()
        val g = game ?: return
        if (g.state.isWon && !recorded) {
            recorded = true
            onScreenPaused() // stop the clock
            viewModelScope.launch { recordWin() }
        }
    }

    private suspend fun recordWin() {
        val g = game ?: return
        val duration = accumulatedMs
        val drawThree = g.state.drawCount == 3
        val prior = computeModeStats(statsDao.all().first(), drawThree)
        statsDao.insert(
            GameRecord(
                endedAtEpochMs = System.currentTimeMillis(),
                drawThree = drawThree,
                won = true,
                durationMs = duration,
                moves = g.moveCount,
            ),
        )
        saveRepo.clear()
        val newStreak = prior.currentStreak + 1
        _sounds.tryEmit(GameSound.WIN)
        _ui.value = _ui.value.copy(
            winSummary = WinSummary(
                durationMs = duration,
                moves = g.moveCount,
                streak = newStreak,
                isFastestWin = prior.fastestWinMs == null || duration < prior.fastestWinMs,
                isFewestMoves = prior.fewestMoves == null || g.moveCount < prior.fewestMoves,
                isBestStreak = newStreak > prior.bestStreak && newStreak > 1,
                dayStreak = settingsRepo.dayStreak.first(),
            ),
        )
    }

    private suspend fun recordAbandonIfNeeded() {
        val g = game ?: return
        if (!g.state.isWon && g.moveCount > 0) {
            statsDao.insert(
                GameRecord(
                    endedAtEpochMs = System.currentTimeMillis(),
                    drawThree = g.state.drawCount == 3,
                    won = false,
                    durationMs = currentElapsed(),
                    moves = g.moveCount,
                ),
            )
        }
    }

    private fun publish(dealing: Boolean) {
        val g = game ?: return
        _ui.value = _ui.value.copy(
            state = g.state,
            canUndo = g.canUndo,
            moveCount = g.moveCount,
            elapsedMs = currentElapsed(),
            isWon = g.state.isWon,
            canAutoComplete = AutoComplete.canAutoComplete(g.state),
            isDealing = dealing,
        )
    }

    private fun persist() {
        val g = game ?: return
        if (g.state.isWon) return
        val save = SavedGame(
            initial = g.initialState,
            moves = g.moves.toList(),
            startedAtEpochMs = startedAtEpochMs,
            elapsedMs = currentElapsed(),
            provenWinnable = provenWinnable,
        )
        viewModelScope.launch(Dispatchers.IO) { saveRepo.save(save) }
    }

    class Factory(
        private val settingsRepo: SettingsRepository,
        private val saveRepo: SaveRepository,
        private val statsDao: GameRecordDao,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameViewModel(settingsRepo, saveRepo, statsDao) as T
    }
}
