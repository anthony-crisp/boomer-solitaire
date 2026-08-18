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

        // 1. Safe foundation plays.
        moves.firstOrNull { move ->
            val card = foundationCard(state, move) ?: return@firstOrNull false
            Solver.isSafeFoundationPlay(state, card)
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

        // 5. Any foundation play.
        moves.firstOrNull { foundationCard(state, it) != null }
            ?.let { return Hint.Suggestion(it) }

        // Deliberately not suggested: card shuffling between tableau columns
        // that uncovers nothing, and digging cards back off the foundations.
        return if (moves.any { it is Move.Draw || it is Move.Recycle }) Hint.DrawFromStock
        else Hint.NoMoves
    }

    private fun foundationCard(state: GameState, move: Move): Card? = when (move) {
        is Move.WasteToFoundation -> state.wasteTop
        is Move.TableauToFoundation -> state.tableau[move.fromColumn].topCard
        else -> null
    }
}
