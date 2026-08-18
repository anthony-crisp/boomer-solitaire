package com.boomersolitaire.engine

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameUndoTest {

    @Test
    fun `undo restores the exact previous state at every depth`() {
        val game = Game(Dealer.deal(77L, 1))
        val snapshots = mutableListOf(game.state)
        val rng = Random(1)
        repeat(120) {
            val moves = Rules.legalMoves(game.state)
            if (moves.isEmpty()) return@repeat
            game.play(moves[rng.nextInt(moves.size)])
            snapshots.add(game.state)
        }
        // Unwind all the way back to the deal, checking each restored state.
        while (game.canUndo) {
            snapshots.removeAt(snapshots.size - 1)
            game.undo()
            assertEquals(snapshots.last(), game.state)
        }
        assertEquals(game.initialState, game.state)
        assertNull(game.undo()) // undo at the deal is a no-op
    }

    @Test
    fun `illegal moves are rejected without changing state or history`() {
        val game = Game(Dealer.deal(3L, 1))
        val before = game.state
        assertNull(game.play(Move.Recycle))
        assertEquals(before, game.state)
        assertEquals(0, game.moveCount)
        assertFalse(game.canUndo)
    }

    @Test
    fun `restore rebuilds state from deal plus moves`() {
        val game = Game(Dealer.deal(2024L, 3))
        val rng = Random(9)
        repeat(80) {
            val moves = Rules.legalMoves(game.state)
            if (moves.isNotEmpty()) game.play(moves[rng.nextInt(moves.size)])
        }
        val restored = Game.restore(game.initialState, game.moves)
        assertEquals(game.state, restored.state)
        assertEquals(game.moveCount, restored.moveCount)
    }

    @Test
    fun `state and moves survive JSON serialisation round-trip`() {
        val json = Json
        val game = Game(Dealer.deal(55L, 1))
        val rng = Random(4)
        repeat(40) {
            val moves = Rules.legalMoves(game.state)
            if (moves.isNotEmpty()) game.play(moves[rng.nextInt(moves.size)])
        }
        val stateJson = json.encodeToString(GameState.serializer(), game.initialState)
        val movesJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(Move.serializer()), game.moves,
        )
        val restored = Game.restore(
            json.decodeFromString(GameState.serializer(), stateJson),
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Move.serializer()), movesJson),
        )
        assertEquals(game.state, restored.state)
    }

    @Test
    fun `play returns new state and records history`() {
        val game = Game(Dealer.deal(11L, 1))
        val next = game.play(Move.Draw)
        assertEquals(next, game.state)
        assertEquals(listOf<Move>(Move.Draw), game.moves)
        assertTrue(game.canUndo)
    }
}
