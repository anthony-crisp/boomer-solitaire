package com.boomersolitaire.engine

/**
 * A game in progress: the initial deal plus the full move history.
 * Undo is unlimited — every played move is kept, and prior states are cached
 * so undo is O(1). The (seed/deck, moves) pair is also the persistence format.
 */
class Game private constructor(
    val initialState: GameState,
    private val history: MutableList<Move>,
    private val states: MutableList<GameState>, // states[i] = state after history[0..i-1]; states[0] = initialState
) {
    constructor(initialState: GameState) : this(initialState, mutableListOf(), mutableListOf(initialState))

    val state: GameState get() = states.last()
    val moves: List<Move> get() = history
    val moveCount: Int get() = history.size
    val canUndo: Boolean get() = history.isNotEmpty()

    /** Attempt [move]; returns the new state, or null if illegal. */
    fun play(move: Move): GameState? {
        val next = Rules.apply(state, move) ?: return null
        history.add(move)
        states.add(next)
        return next
    }

    /** Undo the last move; returns the restored state, or null at the deal. */
    fun undo(): GameState? {
        if (history.isEmpty()) return null
        history.removeAt(history.size - 1)
        states.removeAt(states.size - 1)
        return state
    }

    companion object {
        /** Rebuild a game from its persistence format, dropping any illegal tail. */
        fun restore(initialState: GameState, moves: List<Move>): Game {
            val game = Game(initialState)
            for (move in moves) {
                game.play(move) ?: break
            }
            return game
        }
    }
}
