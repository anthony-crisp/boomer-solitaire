package com.boomersolitaire.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.boomersolitaire.engine.GameState
import com.boomersolitaire.engine.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The persistence format for the game in progress: the initial deal plus the
 * complete move history. Restoring replays the moves, which preserves
 * unlimited undo across process death.
 */
@Serializable
data class SavedGame(
    val initial: GameState,
    val moves: List<Move>,
    val startedAtEpochMs: Long,
    val elapsedMs: Long,
    val provenWinnable: Boolean,
)

private val Context.saveStore by preferencesDataStore(name = "game_save")

class SaveRepository(private val context: Context) {

    private val key = stringPreferencesKey("saved_game")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Decoding the whole game is expensive and grows with the move history,
     * so it happens off the main thread and only for callers that need the
     * game itself. Anything that just needs to know whether a game is waiting
     * should use [hasSavedGame].
     */
    val savedGame: Flow<SavedGame?> = context.saveStore.data
        .map { p -> p[key]?.let { raw -> runCatching { json.decodeFromString<SavedGame>(raw) }.getOrNull() } }
        .flowOn(Dispatchers.Default)

    /** Whether a game is waiting to be resumed — no deserialisation. */
    val hasSavedGame: Flow<Boolean> = context.saveStore.data
        .map { p -> p[key] != null }
        .distinctUntilChanged()

    suspend fun save(game: SavedGame) {
        context.saveStore.edit { it[key] = json.encodeToString(game) }
    }

    suspend fun clear() {
        context.saveStore.edit { it.remove(key) }
    }
}
