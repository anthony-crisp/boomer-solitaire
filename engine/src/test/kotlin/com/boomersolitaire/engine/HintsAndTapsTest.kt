package com.boomersolitaire.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HintsAndTapsTest {

    // ---- Hints ----

    @Test
    fun `hint prefers a safe foundation play`() {
        val state = emptyBoard()
            .withWaste(card(1, Suit.HEARTS))
            .withColumn(0, listOf(card(9, Suit.HEARTS), card(8, Suit.SPADES)))
            .withColumn(1, listOf(card(9, Suit.CLUBS)))
        val hint = Hints.hint(state)
        assertEquals(Hints.Hint.Suggestion(Move.WasteToFoundation(Suit.HEARTS)), hint)
    }

    @Test
    fun `hint prefers uncovering the deepest face-down pile`() {
        val state = emptyBoard()
            .withColumn(0, listOf(card(5, Suit.CLUBS), card(9, Suit.HEARTS)), faceDown = 1)
            .withColumn(1, listOf(card(4, Suit.DIAMONDS), card(6, Suit.CLUBS), card(7, Suit.SPADES), card(9, Suit.DIAMONDS)), faceDown = 3)
            .withColumn(2, listOf(card(10, Suit.SPADES)))
            .withColumn(3, listOf(card(10, Suit.CLUBS)))
        val hint = Hints.hint(state)
        // Both 9s can move onto a black 10; column 1 hides three cards.
        assertTrue(hint is Hints.Hint.Suggestion)
        val move = (hint as Hints.Hint.Suggestion).move as Move.TableauToTableau
        assertEquals(1, move.fromColumn)
        assertEquals(3, move.cardIndex)
    }

    @Test
    fun `hint suggests waste to tableau when no flip is available`() {
        val state = emptyBoard()
            .withStock(card(2, Suit.CLUBS))
            .withWaste(card(9, Suit.HEARTS))
            .withColumn(0, listOf(card(10, Suit.SPADES)))
        assertEquals(Hints.Hint.Suggestion(Move.WasteToTableau(0)), Hints.hint(state))
    }

    @Test
    fun `hint falls back to drawing when a card in the stock is playable`() {
        // Nothing constructive on the table, but the 10 of spades in the
        // stock will land on the jack of diamonds once it is turned up.
        val state = emptyBoard()
            .withStock(card(10, Suit.SPADES))
            .withColumn(0, listOf(card(9, Suit.HEARTS)))
            .withColumn(1, listOf(card(11, Suit.DIAMONDS)))
        assertEquals(Hints.Hint.DrawFromStock, Hints.hint(state))
    }

    @Test
    fun `hint does not send the player through a stock that can never help`() {
        // Turning this deck over forever accomplishes nothing: say so kindly
        // rather than repeating "try drawing".
        val state = emptyBoard()
            .withStock(card(2, Suit.CLUBS))
            .withColumn(0, listOf(card(9, Suit.HEARTS)))
            .withColumn(1, listOf(card(11, Suit.DIAMONDS)))
        assertEquals(Hints.Hint.NoMoves, Hints.hint(state))
    }

    @Test
    fun `hint does not suggest pointless king shuffling`() {
        // A bare king with nothing underneath should not be waved between
        // empty columns — drawing the playable queen is the better advice.
        val state = emptyBoard()
            .withStock(card(12, Suit.SPADES))
            .withColumn(0, listOf(card(13, Suit.HEARTS)))
        assertEquals(Hints.Hint.DrawFromStock, Hints.hint(state))
    }

    @Test
    fun `hint reports no moves on a frozen board`() {
        val state = emptyBoard()
            .withColumn(0, listOf(card(9, Suit.HEARTS)), faceDown = 1)
            .withColumn(1, listOf(card(11, Suit.DIAMONDS)), faceDown = 1)
        assertEquals(Hints.Hint.NoMoves, Hints.hint(state))
    }

    @Test
    fun `hint will not play a waste card up in draw three when it spaces the cycle`() {
        // Both red foundations are on the two, so the three of clubs passes
        // the ordinary tableau-builder safety test — but in draw 3 it is also
        // holding the cycle's spacing, so it must not be recommended.
        val base = emptyBoard(drawCount = 3)
            .withFoundation(Suit.CLUBS, 2)
            .withFoundation(Suit.HEARTS, 2)
            .withFoundation(Suit.DIAMONDS, 2)
            .withWaste(card(3, Suit.CLUBS))
            .withStock(card(7, Suit.SPADES), card(8, Suit.SPADES))
            .withColumn(0, listOf(card(4, Suit.SPADES), card(9, Suit.HEARTS)), faceDown = 1)
            .withColumn(1, listOf(card(10, Suit.SPADES)))
        // In draw 3 the hint should prefer uncovering a card instead.
        assertEquals(
            Hints.Hint.Suggestion(Move.TableauToTableau(0, 1, 1)),
            Hints.hint(base),
        )

        // The identical position in draw 1 has no such dependency, so there
        // the same play is exactly what should be suggested.
        val drawOne = base.copy(drawCount = 1)
        assertEquals(
            Hints.Hint.Suggestion(Move.WasteToFoundation(Suit.CLUBS)),
            Hints.hint(drawOne),
        )
    }

    // ---- Tap-to-move ----

    @Test
    fun `tapping the waste prefers foundation over tableau`() {
        val state = emptyBoard()
            .withFoundation(Suit.HEARTS, 6)
            .withWaste(card(7, Suit.HEARTS))
            .withColumn(0, listOf(card(8, Suit.SPADES)))
        assertEquals(Move.WasteToFoundation(Suit.HEARTS), Taps.bestMove(state, Taps.Source.Waste))
    }

    @Test
    fun `tapping a tableau card finds a tableau home`() {
        val state = emptyBoard()
            .withColumn(0, listOf(card(7, Suit.HEARTS)))
            .withColumn(1, listOf(card(8, Suit.SPADES)))
        assertEquals(Move.TableauToTableau(0, 0, 1), Taps.bestMove(state, Taps.Source.Tableau(0, 0)))
    }

    @Test
    fun `tapping a run head moves the whole run`() {
        val state = emptyBoard()
            .withColumn(0, listOf(card(3, Suit.CLUBS), card(9, Suit.HEARTS), card(8, Suit.SPADES)), faceDown = 1)
            .withColumn(1, listOf(card(10, Suit.CLUBS)))
        assertEquals(Move.TableauToTableau(0, 1, 1), Taps.bestMove(state, Taps.Source.Tableau(0, 1)))
    }

    @Test
    fun `tapping prefers a non-empty destination but will use an empty column`() {
        val state = emptyBoard()
            .withColumn(0, listOf(card(2, Suit.CLUBS), card(13, Suit.HEARTS)), faceDown = 1)
        assertEquals(Move.TableauToTableau(0, 1, 1), Taps.bestMove(state, Taps.Source.Tableau(0, 1)))

        val withRealTarget = emptyBoard()
            .withColumn(0, listOf(card(2, Suit.CLUBS), card(9, Suit.HEARTS)), faceDown = 1)
            .withColumn(1, listOf(card(10, Suit.SPADES)))
            .withColumn(2, emptyList())
        assertEquals(Move.TableauToTableau(0, 1, 1), Taps.bestMove(withRealTarget, Taps.Source.Tableau(0, 1)))
    }

    @Test
    fun `tapping a face-down card or empty pile does nothing`() {
        val state = emptyBoard().withColumn(0, listOf(card(9, Suit.HEARTS), card(8, Suit.SPADES)), faceDown = 1)
        assertNull(Taps.bestMove(state, Taps.Source.Tableau(0, 0)))
        assertNull(Taps.bestMove(state, Taps.Source.Tableau(3, 0)))
        assertNull(Taps.bestMove(emptyBoard(), Taps.Source.Waste))
    }

    @Test
    fun `tapping with no legal destination does nothing`() {
        val state = emptyBoard()
            .withWaste(card(5, Suit.HEARTS))
            .withColumn(0, listOf(card(9, Suit.SPADES)))
        assertNull(Taps.bestMove(state, Taps.Source.Waste))
    }

    @Test
    fun `tapping a foundation card can bring it down to the tableau`() {
        val state = emptyBoard()
            .withFoundation(Suit.HEARTS, 9)
            .withColumn(0, listOf(card(10, Suit.SPADES)))
        assertEquals(
            Move.FoundationToTableau(Suit.HEARTS, 0),
            Taps.bestMove(state, Taps.Source.Foundation(Suit.HEARTS)),
        )
    }
}
