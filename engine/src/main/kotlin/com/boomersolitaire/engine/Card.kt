package com.boomersolitaire.engine

import kotlinx.serialization.Serializable

@Serializable
enum class Suit {
    SPADES, HEARTS, DIAMONDS, CLUBS;

    val isRed: Boolean get() = this == HEARTS || this == DIAMONDS
}

object Rank {
    const val ACE = 1
    const val JACK = 11
    const val QUEEN = 12
    const val KING = 13
}

/**
 * A playing card, identified by a stable id in 0..51.
 * id = suit.ordinal * 13 + (rank - 1)
 */
@Serializable
data class Card(val id: Int) {
    init {
        require(id in 0..51) { "Card id out of range: $id" }
    }

    val suit: Suit get() = Suit.entries[id / 13]
    val rank: Int get() = id % 13 + 1
    val isRed: Boolean get() = suit.isRed

    override fun toString(): String {
        val r = when (rank) {
            Rank.ACE -> "A"
            Rank.JACK -> "J"
            Rank.QUEEN -> "Q"
            Rank.KING -> "K"
            else -> rank.toString()
        }
        val s = when (suit) {
            Suit.SPADES -> "♠"
            Suit.HEARTS -> "♥"
            Suit.DIAMONDS -> "♦"
            Suit.CLUBS -> "♣"
        }
        return "$r$s"
    }

    companion object {
        fun of(suit: Suit, rank: Int): Card {
            require(rank in 1..13) { "Rank out of range: $rank" }
            return Card(suit.ordinal * 13 + (rank - 1))
        }

        val fullDeck: List<Card> = (0..51).map { Card(it) }
    }
}
