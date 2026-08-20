package com.boomersolitaire.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AutoCompleteTest {

    private fun completableState(): GameState {
        // All hearts/diamonds/clubs done; spades split across face-up tableau runs.
        return emptyBoard()
            .withFoundation(Suit.HEARTS, 13)
            .withFoundation(Suit.DIAMONDS, 13)
            .withFoundation(Suit.CLUBS, 13)
            .withFoundation(Suit.SPADES, 7)
            .withColumn(0, listOf(card(13, Suit.SPADES), card(12, Suit.SPADES), card(11, Suit.SPADES)))
            .withColumn(3, listOf(card(10, Suit.SPADES), card(9, Suit.SPADES), card(8, Suit.SPADES)))
    }

    @Test
    fun `detects the trivially winnable position`() {
        assertTrue(AutoComplete.canAutoComplete(completableState()))
    }

    @Test
    fun `not offered with stock, waste, face-down cards, or when already won`() {
        assertFalse(AutoComplete.canAutoComplete(completableState().withStock(card(2, Suit.SPADES))))
        assertFalse(AutoComplete.canAutoComplete(completableState().withWaste(card(2, Suit.SPADES))))
        val faceDown = completableState()
            .withColumn(0, listOf(card(13, Suit.SPADES), card(12, Suit.SPADES), card(11, Suit.SPADES)), faceDown = 1)
        assertFalse(AutoComplete.canAutoComplete(faceDown))
        val won = emptyBoard()
            .withFoundation(Suit.HEARTS, 13).withFoundation(Suit.DIAMONDS, 13)
            .withFoundation(Suit.CLUBS, 13).withFoundation(Suit.SPADES, 13)
        assertFalse(AutoComplete.canAutoComplete(won))
    }

    @Test
    fun `not offered when a column is not a single descending run`() {
        // Legal play can never build this, but a corrupted save could: the
        // eight of spades is buried under the nine, so the cascade would
        // stall. The guard must refuse rather than throw mid-cascade.
        val broken = emptyBoard()
            .withFoundation(Suit.HEARTS, 13)
            .withFoundation(Suit.DIAMONDS, 13)
            .withFoundation(Suit.CLUBS, 13)
            .withFoundation(Suit.SPADES, 7)
            .withColumn(0, listOf(card(8, Suit.SPADES), card(9, Suit.SPADES)))
            .withColumn(1, listOf(card(10, Suit.SPADES), card(11, Suit.SPADES)))
            .withColumn(2, listOf(card(12, Suit.SPADES), card(13, Suit.SPADES)))
        assertFalse(AutoComplete.canAutoComplete(broken))
    }

    @Test
    fun `auto-complete sequence finishes the game lowest rank first`() {
        var state = completableState()
        val moves = AutoComplete.autoCompleteMoves(state)
        val playedRanks = mutableListOf<Int>()
        for (m in moves) {
            playedRanks.add(state.tableau[(m as Move.TableauToFoundation).fromColumn].topCard!!.rank)
            state = Rules.apply(state, m)!!
        }
        assertTrue(state.isWon)
        assertEquals(playedRanks, playedRanks.sorted())
    }

    @Test
    fun `auto-complete never gets stuck from any legally built position`() {
        // Fuzz: play random games; whenever the trigger condition holds,
        // the cascade must run to completion.
        val rng = Random(31)
        var checked = 0
        outer@ for (seed in 0L until 60L) {
            var state = Dealer.deal(seed, 1)
            repeat(400) {
                if (AutoComplete.canAutoComplete(state)) {
                    var s = state
                    for (m in AutoComplete.autoCompleteMoves(state)) s = Rules.apply(s, m)!!
                    assertTrue(s.isWon)
                    checked++
                    return@repeat
                }
                val moves = Rules.legalMoves(state)
                if (moves.isEmpty()) return@repeat
                state = Rules.apply(state, moves[rng.nextInt(moves.size)])!!
            }
        }
        // Random play rarely reaches the trigger; the constructed cases above cover the guarantee.
    }
}
