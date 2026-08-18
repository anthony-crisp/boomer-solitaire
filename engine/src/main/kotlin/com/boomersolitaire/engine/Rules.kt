package com.boomersolitaire.engine

/**
 * Pure rules of Klondike. All functions are side-effect free; [apply] returns
 * the successor state, or null when the move is illegal in the given state.
 *
 * This move generator is the single source of truth for legality — the UI,
 * the hint engine and the winnability solver all go through it.
 */
object Rules {

    /** Can [card] be placed on top of tableau column [column]? */
    fun canPlaceOnTableau(column: TableauColumn, card: Card): Boolean {
        val top = column.topCard ?: return card.rank == Rank.KING
        if (column.faceDownCount == column.cards.size) return false // top card face down
        return top.isRed != card.isRed && top.rank == card.rank + 1
    }

    /** Can [card] go up to its foundation right now? */
    fun canPlaceOnFoundation(state: GameState, card: Card): Boolean =
        state.foundations[card.suit.ordinal] == card.rank - 1

    fun isLegal(state: GameState, move: Move): Boolean = apply(state, move) != null

    fun apply(state: GameState, move: Move): GameState? = when (move) {
        is Move.Draw -> applyDraw(state)
        is Move.Recycle -> applyRecycle(state)
        is Move.WasteToFoundation -> applyWasteToFoundation(state, move)
        is Move.WasteToTableau -> applyWasteToTableau(state, move)
        is Move.TableauToFoundation -> applyTableauToFoundation(state, move)
        is Move.TableauToTableau -> applyTableauToTableau(state, move)
        is Move.FoundationToTableau -> applyFoundationToTableau(state, move)
    }

    private fun applyDraw(state: GameState): GameState? {
        if (state.stock.isEmpty()) return null
        val n = minOf(state.drawCount, state.stock.size)
        val newStock = state.stock.subList(0, state.stock.size - n)
        // Cards flip over one at a time: the last card drawn ends on top of the waste.
        val drawn = state.stock.subList(state.stock.size - n, state.stock.size).reversed()
        return state.copy(stock = newStock.toList(), waste = state.waste + drawn)
    }

    private fun applyRecycle(state: GameState): GameState? {
        if (state.stock.isNotEmpty() || state.waste.isEmpty()) return null
        // The whole waste pile is turned over in one block.
        return state.copy(stock = state.waste.reversed(), waste = emptyList())
    }

    private fun applyWasteToFoundation(state: GameState, move: Move.WasteToFoundation): GameState? {
        val card = state.wasteTop ?: return null
        if (card.suit != move.suit) return null
        if (!canPlaceOnFoundation(state, card)) return null
        return state.copy(
            waste = state.waste.dropLast(1),
            foundations = state.foundations.withIncremented(card.suit.ordinal),
        )
    }

    private fun applyWasteToTableau(state: GameState, move: Move.WasteToTableau): GameState? {
        val card = state.wasteTop ?: return null
        val col = state.tableau.getOrNull(move.toColumn) ?: return null
        if (!canPlaceOnTableau(col, card)) return null
        return state.copy(
            waste = state.waste.dropLast(1),
            tableau = state.tableau.withColumn(move.toColumn, col.copy(cards = col.cards + card)),
        )
    }

    private fun applyTableauToFoundation(state: GameState, move: Move.TableauToFoundation): GameState? {
        val col = state.tableau.getOrNull(move.fromColumn) ?: return null
        val card = col.topCard ?: return null
        if (col.faceDownCount == col.cards.size) return null
        if (!canPlaceOnFoundation(state, card)) return null
        val remaining = col.cards.dropLast(1)
        return state.copy(
            foundations = state.foundations.withIncremented(card.suit.ordinal),
            tableau = state.tableau.withColumn(move.fromColumn, flipIfNeeded(col, remaining)),
        )
    }

    private fun applyTableauToTableau(state: GameState, move: Move.TableauToTableau): GameState? {
        if (move.fromColumn == move.toColumn) return null
        val from = state.tableau.getOrNull(move.fromColumn) ?: return null
        val to = state.tableau.getOrNull(move.toColumn) ?: return null
        if (move.cardIndex < from.faceDownCount || move.cardIndex >= from.cards.size) return null
        val moving = from.cards.subList(move.cardIndex, from.cards.size)
        if (!canPlaceOnTableau(to, moving.first())) return null
        val remaining = from.cards.subList(0, move.cardIndex)
        return state.copy(
            tableau = state.tableau
                .withColumn(move.fromColumn, flipIfNeeded(from, remaining))
                .withColumn(move.toColumn, to.copy(cards = to.cards + moving)),
        )
    }

    private fun applyFoundationToTableau(state: GameState, move: Move.FoundationToTableau): GameState? {
        val card = state.foundationTop(move.suit) ?: return null
        val col = state.tableau.getOrNull(move.toColumn) ?: return null
        if (!canPlaceOnTableau(col, card)) return null
        return state.copy(
            foundations = state.foundations.withIncremented(move.suit.ordinal, -1),
            tableau = state.tableau.withColumn(move.toColumn, col.copy(cards = col.cards + card)),
        )
    }

    /** When the removal leaves a face-down card exposed, it flips face up. */
    private fun flipIfNeeded(col: TableauColumn, remaining: List<Card>): TableauColumn {
        val newFaceDown =
            if (remaining.isNotEmpty() && col.faceDownCount == remaining.size) col.faceDownCount - 1
            else col.faceDownCount
        return TableauColumn(remaining.toList(), newFaceDown)
    }

    /**
     * All legal moves in [state]. Draw/Recycle are included last so callers
     * that scan in order naturally prefer real plays.
     */
    fun legalMoves(state: GameState): List<Move> {
        val moves = ArrayList<Move>()

        // Foundation moves first.
        state.wasteTop?.let { card ->
            if (canPlaceOnFoundation(state, card)) moves += Move.WasteToFoundation(card.suit)
        }
        state.tableau.forEachIndexed { i, col ->
            val top = col.topCard
            if (top != null && col.faceDownCount < col.cards.size && canPlaceOnFoundation(state, top)) {
                moves += Move.TableauToFoundation(i)
            }
        }

        // Tableau-to-tableau runs.
        state.tableau.forEachIndexed { fromIdx, from ->
            for (cardIndex in from.faceDownCount until from.cards.size) {
                val head = from.cards[cardIndex]
                state.tableau.forEachIndexed { toIdx, to ->
                    if (fromIdx != toIdx && canPlaceOnTableau(to, head)) {
                        moves += Move.TableauToTableau(fromIdx, cardIndex, toIdx)
                    }
                }
            }
        }

        // Waste to tableau.
        state.wasteTop?.let { card ->
            state.tableau.forEachIndexed { i, col ->
                if (canPlaceOnTableau(col, card)) moves += Move.WasteToTableau(i)
            }
        }

        // Foundation back to tableau.
        for (suit in Suit.entries) {
            val card = state.foundationTop(suit) ?: continue
            state.tableau.forEachIndexed { i, col ->
                if (canPlaceOnTableau(col, card)) moves += Move.FoundationToTableau(suit, i)
            }
        }

        if (state.stock.isNotEmpty()) moves += Move.Draw
        else if (state.waste.isNotEmpty()) moves += Move.Recycle

        return moves
    }

    private fun List<Int>.withIncremented(index: Int, delta: Int = 1): List<Int> =
        toMutableList().also { it[index] = it[index] + delta }

    private fun List<TableauColumn>.withColumn(index: Int, column: TableauColumn): List<TableauColumn> =
        toMutableList().also { it[index] = column }
}
