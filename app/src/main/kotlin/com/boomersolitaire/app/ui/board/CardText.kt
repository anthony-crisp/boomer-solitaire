package com.boomersolitaire.app.ui.board

import com.boomersolitaire.engine.Card
import com.boomersolitaire.engine.Suit

fun rankLabel(rank: Int): String = when (rank) {
    1 -> "A"
    11 -> "J"
    12 -> "Q"
    13 -> "K"
    else -> rank.toString()
}

fun suitGlyph(suit: Suit): String = when (suit) {
    Suit.SPADES -> "♠"
    Suit.HEARTS -> "♥"
    Suit.DIAMONDS -> "♦"
    Suit.CLUBS -> "♣"
}

fun rankName(rank: Int): String = when (rank) {
    1 -> "ace"; 2 -> "two"; 3 -> "three"; 4 -> "four"; 5 -> "five"; 6 -> "six"
    7 -> "seven"; 8 -> "eight"; 9 -> "nine"; 10 -> "ten"; 11 -> "jack"; 12 -> "queen"
    else -> "king"
}

fun suitName(suit: Suit): String = when (suit) {
    Suit.SPADES -> "spades"
    Suit.HEARTS -> "hearts"
    Suit.DIAMONDS -> "diamonds"
    Suit.CLUBS -> "clubs"
}

fun cardName(card: Card): String = "${rankName(card.rank)} of ${suitName(card.suit)}"

fun describeCard(card: Card, placement: CardPlacement): String {
    if (!placement.faceUp) return "Face-down card"
    val name = cardName(card).replaceFirstChar { it.uppercase() }
    return when (val tap = placement.tap) {
        is TapTarget.Tableau -> "$name, column ${tap.column + 1}. Tap to move it."
        is TapTarget.Foundation -> "$name, on its foundation"
        is TapTarget.Waste -> "$name, top of the waste pile. Tap to play it."
        else -> name
    }
}
