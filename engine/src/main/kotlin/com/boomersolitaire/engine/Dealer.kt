package com.boomersolitaire.engine

import kotlin.random.Random

object Dealer {

    /** Deterministically deal a game from [seed]. */
    fun deal(seed: Long, drawCount: Int): GameState =
        dealFromDeck(Card.fullDeck.shuffled(Random(seed)), drawCount)

    /**
     * Deal from an explicit deck order. Cards are dealt off the FRONT of the
     * list: columns first (one card per column per round, as by hand), then
     * the remaining 24 form the stock with the next card on top.
     */
    fun dealFromDeck(deck: List<Card>, drawCount: Int): GameState {
        require(deck.size == 52 && deck.toSet().size == 52) { "Deck must be a 52-card permutation" }
        var next = 0
        val columns = Array(7) { mutableListOf<Card>() }
        for (round in 0 until 7) {
            for (col in round until 7) {
                columns[col].add(deck[next++])
            }
        }
        val remaining = deck.subList(next, deck.size)
        // Stock top is the LAST list element; the first remaining card is dealt on top.
        val stock = remaining.reversed()
        return GameState(
            stock = stock,
            waste = emptyList(),
            foundations = listOf(0, 0, 0, 0),
            tableau = columns.map { TableauColumn(it.toList(), faceDownCount = it.size - 1) },
            drawCount = drawCount,
        )
    }
}
