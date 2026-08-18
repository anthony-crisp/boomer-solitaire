package com.boomersolitaire.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SolverTest {

    @Test
    fun `safe foundation play rule`() {
        // Aces and twos are always safe.
        val fresh = emptyBoard()
        assertTrue(Solver.isSafeFoundationPlay(fresh, card(1, Suit.SPADES)))
        assertTrue(Solver.isSafeFoundationPlay(fresh, card(2, Suit.HEARTS)))
        // A black 5 is safe only once both red foundations reach 4.
        val state = emptyBoard()
            .withFoundation(Suit.SPADES, 4)
            .withFoundation(Suit.HEARTS, 4)
            .withFoundation(Suit.DIAMONDS, 3)
        assertFalse(Solver.isSafeFoundationPlay(state, card(5, Suit.SPADES)))
        val ready = state.withFoundation(Suit.DIAMONDS, 4)
        assertTrue(Solver.isSafeFoundationPlay(ready, card(5, Suit.SPADES)))
    }

    @Test
    fun `normalize plays safe cards up automatically`() {
        val state = emptyBoard()
            .withWaste(card(1, Suit.SPADES))
            .withColumn(0, listOf(card(1, Suit.HEARTS)))
        val normalized = Solver.normalize(state)
        assertEquals(1, normalized.foundations[Suit.SPADES.ordinal])
        assertEquals(1, normalized.foundations[Suit.HEARTS.ordinal])
        assertTrue(normalized.waste.isEmpty())
        assertTrue(normalized.tableau[0].cards.isEmpty())
    }

    @Test
    fun `solves a nearly finished position instantly`() {
        val state = emptyBoard()
            .withFoundation(Suit.HEARTS, 13)
            .withFoundation(Suit.DIAMONDS, 13)
            .withFoundation(Suit.CLUBS, 13)
            .withFoundation(Suit.SPADES, 10)
            .withColumn(0, listOf(card(13, Suit.SPADES), card(12, Suit.SPADES), card(11, Suit.SPADES)))
        assertEquals(Solver.Result.SOLVED, Solver.solve(state, maxNodes = 1_000))
    }

    @Test
    fun `flipping a buried card is found by the search`() {
        // K♠ on top of face-down Q♠: K♠ → empty column frees Q♠,
        // Q♠ plays up, K♠ follows.
        val state = emptyBoard()
            .withFoundation(Suit.HEARTS, 13)
            .withFoundation(Suit.DIAMONDS, 13)
            .withFoundation(Suit.CLUBS, 13)
            .withFoundation(Suit.SPADES, 11)
            .withColumn(0, listOf(card(12, Suit.SPADES), card(13, Suit.SPADES)), faceDown = 1)
        assertEquals(Solver.Result.SOLVED, Solver.solve(state, maxNodes = 10_000))
    }

    @Test
    fun `reports a dead position unsolvable`() {
        // Free cards: Q♠, K♠, K♥ (everything else is on foundations).
        // K♥ can play up and flip Q♠, and Q♠ follows — but K♠ sits alone as a
        // face-down column top, which nothing can ever flip. UNSOLVABLE.
        val dead = emptyBoard()
            .withFoundation(Suit.DIAMONDS, 13)
            .withFoundation(Suit.CLUBS, 13)
            .withFoundation(Suit.SPADES, 11)
            .withFoundation(Suit.HEARTS, 12)
            .withColumn(0, listOf(card(12, Suit.SPADES), card(13, Suit.HEARTS)), faceDown = 1)
            .withColumn(1, listOf(card(13, Suit.SPADES)), faceDown = 1)
        assertEquals(Solver.Result.UNSOLVABLE, Solver.solve(dead, maxNodes = 10_000))
    }

    @Test
    fun `solver proves most draw-1 deals winnable within budget`() {
        var solved = 0
        val attempts = 20
        val start = System.currentTimeMillis()
        for (seed in 100L until 100L + attempts) {
            val state = Dealer.deal(seed, 1)
            if (Solver.solve(state, maxNodes = 200_000) == Solver.Result.SOLVED) solved++
        }
        val elapsed = System.currentTimeMillis() - start
        // ~80%+ of draw-1 deals are winnable; the solver should prove most.
        assertTrue("Only $solved/$attempts proven winnable", solved >= attempts * 6 / 10)
        assertTrue("Solver too slow: ${elapsed}ms for $attempts deals", elapsed < 60_000)
        println("Solver: $solved/$attempts draw-1 deals proven winnable in ${elapsed}ms")
    }

    @Test
    fun `winnable dealer produces proven-winnable deals`() {
        val deal = WinnableDealer.winnableDeal(1, Random(5))
        assertTrue(deal.provenWinnable)
        assertInvariants(deal.state)
        assertEquals(deal.state, Dealer.deal(deal.seed, 1))
    }

    @Test
    fun `winnable dealer works for draw 3`() {
        val deal = WinnableDealer.winnableDeal(3, Random(6))
        assertTrue(deal.provenWinnable)
        assertEquals(3, deal.state.drawCount)
    }
}
