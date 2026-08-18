package com.boomersolitaire.engine

object AutoComplete {

    /**
     * The game is trivially winnable when the stock and waste are empty and
     * every tableau card is face up: from there the lowest needed card is
     * always on top of some column, so the cascade can never get stuck.
     */
    fun canAutoComplete(state: GameState): Boolean =
        !state.isWon &&
            state.stock.isEmpty() &&
            state.waste.isEmpty() &&
            state.tableau.all { it.faceDownCount == 0 }

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
