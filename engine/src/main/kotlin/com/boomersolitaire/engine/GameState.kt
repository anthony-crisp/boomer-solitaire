package com.boomersolitaire.engine

import kotlinx.serialization.Serializable

/**
 * One tableau column. [cards] runs from the base of the pile (index 0) to the
 * exposed top card (last index). The first [faceDownCount] cards are face down.
 */
@Serializable
data class TableauColumn(
    val cards: List<Card> = emptyList(),
    val faceDownCount: Int = 0,
) {
    init {
        require(faceDownCount in 0..cards.size) {
            "faceDownCount $faceDownCount invalid for pile of ${cards.size}"
        }
        require(cards.isNotEmpty() || faceDownCount == 0)
    }

    val topCard: Card? get() = cards.lastOrNull()
    val faceUpCards: List<Card> get() = cards.subList(faceDownCount, cards.size)
    fun isFaceUp(index: Int): Boolean = index >= faceDownCount
}

/**
 * Immutable snapshot of a Klondike game.
 *
 * Conventions:
 *  - The top of [stock] and [waste] is the LAST element of each list.
 *  - [foundations] holds the number of cards on each foundation, indexed by
 *    [Suit.ordinal]. A foundation for suit s with count c contains exactly
 *    ace..c of that suit; its top card is Card.of(s, c).
 */
@Serializable
data class GameState(
    val stock: List<Card>,
    val waste: List<Card>,
    val foundations: List<Int>,
    val tableau: List<TableauColumn>,
    val drawCount: Int,
) {
    init {
        require(foundations.size == 4)
        require(tableau.size == 7)
        require(drawCount == 1 || drawCount == 3)
    }

    val wasteTop: Card? get() = waste.lastOrNull()

    fun foundationTop(suit: Suit): Card? {
        val count = foundations[suit.ordinal]
        return if (count == 0) null else Card.of(suit, count)
    }

    val isWon: Boolean get() = foundations.all { it == 13 }

    val faceDownCardCount: Int get() = tableau.sumOf { it.faceDownCount }
}
