package com.boomersolitaire.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardAndDealerTest {

    @Test
    fun `card ids map to suits and ranks`() {
        assertEquals(Suit.SPADES, Card(0).suit)
        assertEquals(Rank.ACE, Card(0).rank)
        assertEquals(Suit.SPADES, Card(12).suit)
        assertEquals(Rank.KING, Card(12).rank)
        assertEquals(Suit.HEARTS, Card(13).suit)
        assertEquals(Rank.ACE, Card(13).rank)
        assertEquals(Suit.CLUBS, Card(51).suit)
        assertEquals(Rank.KING, Card(51).rank)
        for (id in 0..51) {
            val c = Card(id)
            assertEquals(c, Card.of(c.suit, c.rank))
        }
    }

    @Test
    fun `colours are correct`() {
        assertFalse(Card.of(Suit.SPADES, 1).isRed)
        assertFalse(Card.of(Suit.CLUBS, 5).isRed)
        assertTrue(Card.of(Suit.HEARTS, 9).isRed)
        assertTrue(Card.of(Suit.DIAMONDS, 13).isRed)
    }

    @Test
    fun `full deck has 52 unique cards`() {
        assertEquals(52, Card.fullDeck.size)
        assertEquals(52, Card.fullDeck.toSet().size)
    }

    @Test
    fun `deal has correct structure`() {
        val state = Dealer.deal(seed = 42L, drawCount = 1)
        assertEquals(24, state.stock.size)
        assertTrue(state.waste.isEmpty())
        assertEquals(listOf(0, 0, 0, 0), state.foundations)
        assertEquals(7, state.tableau.size)
        state.tableau.forEachIndexed { i, col ->
            assertEquals("Column $i size", i + 1, col.cards.size)
            assertEquals("Column $i face-down", i, col.faceDownCount)
        }
        assertInvariants(state)
    }

    @Test
    fun `deal is deterministic per seed and varies across seeds`() {
        assertEquals(Dealer.deal(7L, 1), Dealer.deal(7L, 1))
        assertNotEquals(Dealer.deal(7L, 1), Dealer.deal(8L, 1))
    }

    @Test
    fun `cards are dealt round-robin by column then stock from the top`() {
        val deck = Card.fullDeck // unshuffled, in id order
        val state = Dealer.dealFromDeck(deck, 1)
        // First card goes to column 0, second begins the next column's round.
        assertEquals(deck[0], state.tableau[0].cards[0])
        assertEquals(deck[1], state.tableau[1].cards[0])
        assertEquals(deck[7], state.tableau[1].cards[1])
        // 28th card (index 27) is the last tableau card, 29th starts the stock.
        assertEquals(deck[27], state.tableau[6].cards[6])
        assertEquals(deck[28], state.stock.last()) // stock top = last element
        assertEquals(deck[51], state.stock.first())
    }

    @Test
    fun `dealFromDeck rejects bad decks`() {
        try {
            Dealer.dealFromDeck(Card.fullDeck.dropLast(1), 1)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            Dealer.dealFromDeck(Card.fullDeck.dropLast(1) + Card(0), 1)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }
}
