package com.boomersolitaire.engine

/**
 * Tap-to-move: resolve a tap on a card to the best legal destination.
 * Foundations are preferred, then the tableau. Never requires precision.
 */
object Taps {

    sealed class Source {
        data object Waste : Source()

        /** A tap on the card at [cardIndex] in tableau column [column]. */
        data class Tableau(val column: Int, val cardIndex: Int) : Source()

        data class Foundation(val suit: Suit) : Source()
    }

    fun bestMove(state: GameState, source: Source): Move? = when (source) {
        is Source.Waste -> bestWasteMove(state)
        is Source.Tableau -> bestTableauMove(state, source.column, source.cardIndex)
        is Source.Foundation -> bestFoundationMove(state, source.suit)
    }

    private fun bestWasteMove(state: GameState): Move? {
        val card = state.wasteTop ?: return null
        if (Rules.canPlaceOnFoundation(state, card)) return Move.WasteToFoundation(card.suit)
        state.tableau.forEachIndexed { i, col ->
            if (Rules.canPlaceOnTableau(col, card)) return Move.WasteToTableau(i)
        }
        return null
    }

    private fun bestTableauMove(state: GameState, column: Int, cardIndex: Int): Move? {
        val col = state.tableau.getOrNull(column) ?: return null
        if (cardIndex < col.faceDownCount || cardIndex >= col.cards.size) return null
        val isTop = cardIndex == col.cards.size - 1

        if (isTop && Rules.canPlaceOnFoundation(state, col.cards[cardIndex])) {
            return Move.TableauToFoundation(column)
        }

        val head = col.cards[cardIndex]
        // Prefer destinations that aren't empty; use an empty column as a last resort.
        var emptyTarget: Move? = null
        state.tableau.forEachIndexed { i, dest ->
            if (i != column && Rules.canPlaceOnTableau(dest, head)) {
                if (dest.cards.isEmpty()) {
                    if (emptyTarget == null) emptyTarget = Move.TableauToTableau(column, cardIndex, i)
                } else {
                    return Move.TableauToTableau(column, cardIndex, i)
                }
            }
        }
        return emptyTarget
    }

    private fun bestFoundationMove(state: GameState, suit: Suit): Move? {
        val card = state.foundationTop(suit) ?: return null
        state.tableau.forEachIndexed { i, col ->
            if (col.cards.isNotEmpty() && Rules.canPlaceOnTableau(col, card)) {
                return Move.FoundationToTableau(suit, i)
            }
        }
        return null
    }
}
