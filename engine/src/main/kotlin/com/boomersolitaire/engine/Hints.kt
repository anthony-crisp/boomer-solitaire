package com.boomersolitaire.engine

/**
 * Picks one legal, genuinely useful move to suggest.
 * Priority: safe foundation plays, then moves that uncover face-down cards,
 * then constructive waste/king plays, then any foundation play, then drawing.
 */
object Hints {

    sealed class Hint {
        /** Highlight this move's source card and destination pile. */
        data class Suggestion(val move: Move) : Hint()

        /** Nothing constructive on the board: suggest turning the stock. */
        data object DrawFromStock : Hint()

        /** No moves at all (extremely rare with unlimited passes). */
        data object NoMoves : Hint()
    }

    fun hint(state: GameState): Hint {
        val moves = Rules.legalMoves(state)
        if (moves.isEmpty()) return Hint.NoMoves

        // 1. Safe foundation plays. A waste card needs the stricter test:
        // in draw-3 it may be holding the draw cycle's spacing together.
        moves.firstOrNull { move ->
            val card = foundationCard(state, move) ?: return@firstOrNull false
            when (move) {
                is Move.WasteToFoundation -> Solver.isSafeWasteFoundationPlay(state, card)
                else -> Solver.isSafeFoundationPlay(state, card)
            }
        }?.let { return Hint.Suggestion(it) }

        // 2. Tableau moves that flip a face-down card (deepest pile first).
        moves.filterIsInstance<Move.TableauToTableau>()
            .filter { m ->
                val from = state.tableau[m.fromColumn]
                m.cardIndex == from.faceDownCount && from.faceDownCount > 0
            }
            .maxByOrNull { state.tableau[it.fromColumn].faceDownCount }
            ?.let { return Hint.Suggestion(it) }

        // Foundation plays that expose a face-down card count as flips too.
        moves.filterIsInstance<Move.TableauToFoundation>()
            .firstOrNull { m ->
                val col = state.tableau[m.fromColumn]
                col.faceDownCount > 0 && col.cards.size == col.faceDownCount + 1
            }
            ?.let { return Hint.Suggestion(it) }

        // 3. Waste to tableau — gets a buried stock card into play.
        moves.filterIsInstance<Move.WasteToTableau>().firstOrNull()
            ?.let { return Hint.Suggestion(it) }

        // 4. A king (with something underneath it) onto an empty column.
        moves.filterIsInstance<Move.TableauToTableau>()
            .firstOrNull { m ->
                state.tableau[m.toColumn].cards.isEmpty() &&
                    (m.cardIndex > 0 || state.tableau[m.fromColumn].faceDownCount > 0)
            }
            ?.let { return Hint.Suggestion(it) }

        // 5. Any tableau card that can go up.
        moves.filterIsInstance<Move.TableauToFoundation>().firstOrNull()
            ?.let { return Hint.Suggestion(it) }

        // 6. Drawing — but only if a card somewhere in the cycle can actually
        // be played. Turning the deck forever is busywork, and saying so
        // plainly is kinder than an endless "try drawing".
        val canDraw = moves.any { it is Move.Draw || it is Move.Recycle }
        if (canDraw && drawCycleHasAPlay(state)) return Hint.DrawFromStock

        // 7. Last resort: send a waste card up. In draw 3 this can disturb the
        // draw cycle's spacing, which is why it ranks below simply drawing —
        // but it beats telling the player there is nothing to do.
        moves.filterIsInstance<Move.WasteToFoundation>().firstOrNull()
            ?.let { return Hint.Suggestion(it) }

        // Deliberately never suggested: card shuffling between tableau columns
        // that uncovers nothing, and digging cards back off the foundations.
        return Hint.NoMoves
    }

    /**
     * Walk one full turn of the draw cycle and report whether any card that
     * surfaces could be played to a foundation or a tableau column.
     */
    private fun drawCycleHasAPlay(start: GameState): Boolean {
        var state = start
        val seen = HashSet<Int>()
        seen.add(cycleKey(state))
        while (true) {
            state = when {
                state.stock.isNotEmpty() -> Rules.apply(state, Move.Draw) ?: return false
                state.waste.isNotEmpty() -> Rules.apply(state, Move.Recycle) ?: return false
                else -> return false
            }
            if (!seen.add(cycleKey(state))) return false
            val card = state.wasteTop ?: continue
            if (Rules.canPlaceOnFoundation(state, card)) return true
            if (state.tableau.any { Rules.canPlaceOnTableau(it, card) }) return true
        }
    }

    /** Identity of a stock/waste configuration within the draw cycle. */
    private fun cycleKey(state: GameState): Int =
        state.stock.size * 64 + (state.wasteTop?.id ?: 53)

    private fun foundationCard(state: GameState, move: Move): Card? = when (move) {
        is Move.WasteToFoundation -> state.wasteTop
        is Move.TableauToFoundation -> state.tableau[move.fromColumn].topCard
        else -> null
    }
}
