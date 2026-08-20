package com.boomersolitaire.engine

object AutoComplete {

    /**
     * The game is trivially winnable when the stock and waste are empty, every
     * tableau card is face up, and each column is a single descending run.
     *
     * That last condition is what makes each column's top card its lowest
     * rank, which is why the cascade can never get stuck. Legal play always
     * maintains it, so this is a guard against a corrupted saved game rather
     * than against the rules — but it is the actual precondition, so it is
     * worth checking rather than assuming.
     */
    fun canAutoComplete(state: GameState): Boolean =
        !state.isWon &&
            state.stock.isEmpty() &&
            state.waste.isEmpty() &&
            state.tableau.all { it.faceDownCount == 0 && isDescendingRun(it) }

    private fun isDescendingRun(column: TableauColumn): Boolean {
        for (i in 1 until column.cards.size) {
            if (column.cards[i - 1].rank != column.cards[i].rank + 1) return false
        }
        return true
    }

    /**
     * The full sequence of foundation moves that finishes the game,
     * lowest rank first for a pleasing cascade.
     */
    fun autoCompleteMoves(start: GameState): List<Move> {
        require(canAutoComplete(start)) { "Not in an auto-completable position" }
        val moves = ArrayList<Move>()
        var state = start
        while (!state.isWon) {
            val move = state.tableau.withIndex()
                .filter { (_, col) -> col.topCard != null && Rules.canPlaceOnFoundation(state, col.topCard!!) }
                .minByOrNull { (_, col) -> col.topCard!!.rank }
                ?.let { (i, _) -> Move.TableauToFoundation(i) }
                ?: error("Auto-complete got stuck — should be impossible")
            state = Rules.apply(state, move)!!
            moves.add(move)
        }
        return moves
    }
}
