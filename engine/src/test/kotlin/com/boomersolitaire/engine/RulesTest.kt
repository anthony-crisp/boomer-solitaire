package com.boomersolitaire.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    // ---- Draw & recycle ----

    @Test
    fun `draw 1 moves top stock card to waste top`() {
        val a = card(4, Suit.HEARTS)
        val b = card(9, Suit.CLUBS)
        val state = emptyBoard(drawCount = 1).withStock(a, b) // b is stock top
        val next = Rules.apply(state, Move.Draw)!!
        assertEquals(listOf(a), next.stock)
        assertEquals(listOf(b), next.waste)
    }

    @Test
    fun `draw 3 flips cards one at a time so third drawn is waste top`() {
        val c1 = card(2, Suit.HEARTS)
        val c2 = card(3, Suit.HEARTS)
        val c3 = card(4, Suit.HEARTS)
        val c4 = card(5, Suit.HEARTS)
        val state = emptyBoard(drawCount = 3).withStock(c1, c2, c3, c4) // c4 on top
        val next = Rules.apply(state, Move.Draw)!!
        assertEquals(listOf(c1), next.stock)
        // Drawn in order c4, c3, c2 → waste bottom-to-top is c4, c3, c2.
        assertEquals(listOf(c4, c3, c2), next.waste)
        assertEquals(c2, next.wasteTop)
    }

    @Test
    fun `draw 3 with fewer cards draws what remains`() {
        val c1 = card(2, Suit.HEARTS)
        val c2 = card(3, Suit.HEARTS)
        val state = emptyBoard(drawCount = 3).withStock(c1, c2)
        val next = Rules.apply(state, Move.Draw)!!
        assertTrue(next.stock.isEmpty())
        assertEquals(listOf(c2, c1), next.waste)
    }

    @Test
    fun `draw from empty stock is illegal`() {
        assertNull(Rules.apply(emptyBoard(), Move.Draw))
    }

    @Test
    fun `recycle turns waste over as a block`() {
        val c1 = card(2, Suit.HEARTS)
        val c2 = card(3, Suit.HEARTS)
        val c3 = card(4, Suit.HEARTS)
        val state = emptyBoard().withWaste(c1, c2, c3)
        val next = Rules.apply(state, Move.Recycle)!!
        assertTrue(next.waste.isEmpty())
        assertEquals(listOf(c3, c2, c1), next.stock) // c1 back on top
        assertEquals(c1, next.stock.last())
    }

    @Test
    fun `recycle illegal while stock has cards or waste empty`() {
        assertNull(Rules.apply(emptyBoard().withStock(card(2, Suit.HEARTS)).withWaste(card(3, Suit.CLUBS)), Move.Recycle))
        assertNull(Rules.apply(emptyBoard(), Move.Recycle))
    }

    @Test
    fun `full pass and recycle repeats the same draw order`() {
        var state = Dealer.deal(99L, 1)
        val firstPass = mutableListOf<Card>()
        while (state.stock.isNotEmpty()) {
            state = Rules.apply(state, Move.Draw)!!
            firstPass.add(state.wasteTop!!)
        }
        state = Rules.apply(state, Move.Recycle)!!
        val secondPass = mutableListOf<Card>()
        while (state.stock.isNotEmpty()) {
            state = Rules.apply(state, Move.Draw)!!
            secondPass.add(state.wasteTop!!)
        }
        assertEquals(firstPass, secondPass)
    }

    // ---- Foundation moves ----

    @Test
    fun `ace then two go up in suit order only`() {
        var state = emptyBoard().withWaste(card(1, Suit.SPADES))
        assertNull(Rules.apply(state, Move.WasteToFoundation(Suit.HEARTS))) // wrong suit param
        state = Rules.apply(state, Move.WasteToFoundation(Suit.SPADES))!!
        assertEquals(1, state.foundations[Suit.SPADES.ordinal])
        assertTrue(state.waste.isEmpty())

        // 3 of spades cannot follow the ace.
        val bad = state.withWaste(card(3, Suit.SPADES))
        assertNull(Rules.apply(bad, Move.WasteToFoundation(Suit.SPADES)))
        // 2 of hearts cannot go on the spades foundation.
        val wrongSuit = state.withWaste(card(2, Suit.HEARTS))
        assertNull(Rules.apply(wrongSuit, Move.WasteToFoundation(Suit.HEARTS)))
        // 2 of spades can.
        val good = state.withWaste(card(2, Suit.SPADES))
        assertEquals(2, Rules.apply(good, Move.WasteToFoundation(Suit.SPADES))!!.foundations[Suit.SPADES.ordinal])
    }

    @Test
    fun `tableau top card to foundation flips the card underneath`() {
        val hidden = card(9, Suit.DIAMONDS)
        val state = emptyBoard()
            .withColumn(0, listOf(hidden, card(1, Suit.CLUBS)), faceDown = 1)
        val next = Rules.apply(state, Move.TableauToFoundation(0))!!
        assertEquals(1, next.foundations[Suit.CLUBS.ordinal])
        assertEquals(listOf(hidden), next.tableau[0].cards)
        assertEquals(0, next.tableau[0].faceDownCount) // flipped
    }

    @Test
    fun `foundation move from empty or face-down column is illegal`() {
        assertNull(Rules.apply(emptyBoard(), Move.TableauToFoundation(0)))
    }

    @Test
    fun `foundation card can come back to the tableau`() {
        val state = emptyBoard()
            .withFoundation(Suit.HEARTS, 5)
            .withColumn(2, listOf(card(6, Suit.SPADES)))
        val next = Rules.apply(state, Move.FoundationToTableau(Suit.HEARTS, 2))!!
        assertEquals(4, next.foundations[Suit.HEARTS.ordinal])
        assertEquals(listOf(card(6, Suit.SPADES), card(5, Suit.HEARTS)), next.tableau[2].cards)
        // And an illegal target is rejected.
        assertNull(Rules.apply(state, Move.FoundationToTableau(Suit.HEARTS, 0)))
    }

    // ---- Tableau moves ----

    @Test
    fun `only kings may fill an empty column`() {
        val king = emptyBoard().withWaste(card(13, Suit.HEARTS))
        assertNotNull(Rules.apply(king, Move.WasteToTableau(3)))
        val queen = emptyBoard().withWaste(card(12, Suit.HEARTS))
        assertNull(Rules.apply(queen, Move.WasteToTableau(3)))
    }

    @Test
    fun `tableau build must alternate colours descending by one`() {
        val base = emptyBoard().withColumn(0, listOf(card(8, Suit.SPADES)))
        assertNotNull(Rules.apply(base.withWaste(card(7, Suit.HEARTS)), Move.WasteToTableau(0)))
        assertNotNull(Rules.apply(base.withWaste(card(7, Suit.DIAMONDS)), Move.WasteToTableau(0)))
        assertNull(Rules.apply(base.withWaste(card(7, Suit.CLUBS)), Move.WasteToTableau(0))) // same colour
        assertNull(Rules.apply(base.withWaste(card(6, Suit.HEARTS)), Move.WasteToTableau(0))) // wrong rank
        assertNull(Rules.apply(base.withWaste(card(9, Suit.HEARTS)), Move.WasteToTableau(0))) // wrong direction
    }

    @Test
    fun `cannot build on a face-down top card`() {
        val state = emptyBoard()
            .withColumn(0, listOf(card(8, Suit.SPADES)), faceDown = 1)
            .withWaste(card(7, Suit.HEARTS))
        assertNull(Rules.apply(state, Move.WasteToTableau(0)))
    }

    @Test
    fun `moving a run carries all cards above and flips beneath`() {
        val hidden = card(2, Suit.CLUBS)
        val state = emptyBoard()
            .withColumn(0, listOf(hidden, card(9, Suit.HEARTS), card(8, Suit.SPADES), card(7, Suit.DIAMONDS)), faceDown = 1)
            .withColumn(1, listOf(card(10, Suit.CLUBS)))
        val next = Rules.apply(state, Move.TableauToTableau(0, 1, 1))!!
        assertEquals(listOf(hidden), next.tableau[0].cards)
        assertEquals(0, next.tableau[0].faceDownCount)
        assertEquals(
            listOf(card(10, Suit.CLUBS), card(9, Suit.HEARTS), card(8, Suit.SPADES), card(7, Suit.DIAMONDS)),
            next.tableau[1].cards,
        )
    }

    @Test
    fun `moving a partial run leaves the rest unflipped`() {
        val state = emptyBoard()
            .withColumn(0, listOf(card(9, Suit.HEARTS), card(8, Suit.SPADES), card(7, Suit.DIAMONDS)))
            .withColumn(1, listOf(card(9, Suit.DIAMONDS)))
        val next = Rules.apply(state, Move.TableauToTableau(0, 1, 1))!!
        assertEquals(listOf(card(9, Suit.HEARTS)), next.tableau[0].cards)
        assertEquals(0, next.tableau[0].faceDownCount)
        assertEquals(listOf(card(9, Suit.DIAMONDS), card(8, Suit.SPADES), card(7, Suit.DIAMONDS)), next.tableau[1].cards)
    }

    @Test
    fun `cannot grab a face-down card or move to same column`() {
        val state = emptyBoard()
            .withColumn(0, listOf(card(9, Suit.HEARTS), card(8, Suit.SPADES)), faceDown = 1)
            .withColumn(1, listOf(card(10, Suit.CLUBS)))
        assertNull(Rules.apply(state, Move.TableauToTableau(0, 0, 1))) // face-down grab
        assertNull(Rules.apply(state, Move.TableauToTableau(0, 1, 0))) // same column
        assertNull(Rules.apply(state, Move.TableauToTableau(0, 5, 1))) // out of range
    }

    @Test
    fun `emptying a column leaves it usable by a king`() {
        val state = emptyBoard()
            .withColumn(0, listOf(card(5, Suit.HEARTS)))
            .withColumn(1, listOf(card(6, Suit.SPADES)))
            .withColumn(2, listOf(card(13, Suit.CLUBS)))
        var next = Rules.apply(state, Move.TableauToTableau(0, 0, 1))!!
        assertTrue(next.tableau[0].cards.isEmpty())
        assertEquals(0, next.tableau[0].faceDownCount)
        next = Rules.apply(next, Move.TableauToTableau(2, 0, 0))!!
        assertEquals(listOf(card(13, Suit.CLUBS)), next.tableau[0].cards)
    }

    // ---- Move generator ----

    @Test
    fun `every generated legal move actually applies`() {
        var state = Dealer.deal(1234L, 1)
        repeat(200) {
            val moves = Rules.legalMoves(state)
            for (m in moves) {
                assertNotNull("Generated move $m must apply", Rules.apply(state, m))
            }
            if (moves.isEmpty()) return
            state = Rules.apply(state, moves.first())!!
            assertInvariants(state)
        }
    }

    @Test
    fun `legal moves on a fresh deal include draw`() {
        val state = Dealer.deal(5L, 1)
        assertTrue(Rules.legalMoves(state).contains(Move.Draw))
        assertFalse(Rules.legalMoves(state).contains(Move.Recycle))
    }
}
