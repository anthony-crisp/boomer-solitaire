package com.boomersolitaire.engine

import org.junit.Assert.assertTrue

fun card(rank: Int, suit: Suit): Card = Card.of(suit, rank)

fun emptyBoard(drawCount: Int = 1): GameState = GameState(
    stock = emptyList(),
    waste = emptyList(),
    foundations = listOf(0, 0, 0, 0),
    tableau = List(7) { TableauColumn() },
    drawCount = drawCount,
)

fun GameState.withColumn(index: Int, cards: List<Card>, faceDown: Int = 0): GameState =
    copy(tableau = tableau.toMutableList().also { it[index] = TableauColumn(cards, faceDown) })

fun GameState.withStock(vararg cards: Card): GameState = copy(stock = cards.toList())
fun GameState.withWaste(vararg cards: Card): GameState = copy(waste = cards.toList())
fun GameState.withFoundation(suit: Suit, count: Int): GameState =
    copy(foundations = foundations.toMutableList().also { it[suit.ordinal] = count })

/** Assert the deep structural invariants that must hold for any reachable state of a full-deck game. */
fun assertInvariants(state: GameState) {
    val zoneCards = state.stock + state.waste + state.tableau.flatMap { it.cards }
    val foundationCards = Suit.entries.flatMap { suit ->
        (1..state.foundations[suit.ordinal]).map { Card.of(suit, it) }
    }
    val all = zoneCards + foundationCards
    assertTrue("52 cards expected, got ${all.size}", all.size == 52)
    assertTrue("Duplicate cards detected", all.toSet().size == 52)

    for ((i, col) in state.tableau.withIndex()) {
        assertTrue("Column $i faceDownCount invalid", col.faceDownCount in 0..col.cards.size)
        // Face-up run must descend by one, alternating colours.
        val faceUp = col.faceUpCards
        for (j in 1 until faceUp.size) {
            val upper = faceUp[j - 1]
            val lower = faceUp[j]
            assertTrue(
                "Column $i face-up run broken at $upper -> $lower",
                upper.rank == lower.rank + 1 && upper.isRed != lower.isRed,
            )
        }
    }
    for (count in state.foundations) assertTrue(count in 0..13)
}
