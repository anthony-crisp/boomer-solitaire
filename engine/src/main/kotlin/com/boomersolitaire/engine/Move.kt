package com.boomersolitaire.engine

import kotlinx.serialization.Serializable

@Serializable
sealed class Move {
    /** Turn up to drawCount cards from the stock onto the waste. */
    @Serializable
    data object Draw : Move()

    /** Turn the exhausted waste pile back over to form a new stock. */
    @Serializable
    data object Recycle : Move()

    @Serializable
    data class WasteToFoundation(val suit: Suit) : Move()

    @Serializable
    data class WasteToTableau(val toColumn: Int) : Move()

    @Serializable
    data class TableauToFoundation(val fromColumn: Int) : Move()

    /**
     * Move the run of face-up cards starting at [cardIndex] (within the
     * source column's card list) from [fromColumn] onto [toColumn].
     */
    @Serializable
    data class TableauToTableau(val fromColumn: Int, val cardIndex: Int, val toColumn: Int) : Move()

    @Serializable
    data class FoundationToTableau(val suit: Suit, val toColumn: Int) : Move()
}
