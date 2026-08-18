package com.boomersolitaire.app.ui.board

import androidx.compose.ui.geometry.Offset
import com.boomersolitaire.app.data.CardSize
import com.boomersolitaire.engine.GameState
import com.boomersolitaire.engine.Suit

/** All pixel geometry for one board size. */
data class BoardMetrics(
    val boardW: Float,
    val boardH: Float,
    val cardW: Float,
    val cardH: Float,
    val stockPos: Offset,
    val wastePos: Offset,
    val wasteFanDx: Float,
    val foundationPos: List<Offset>, // indexed by Suit.ordinal
    val tableauX: List<Float>,
    val tableauTopY: Float,
    val faceUpDy: Float,
    val faceDownDy: Float,
    val indexScale: Float,
    val isLandscape: Boolean,
)

sealed class TapTarget {
    data object Stock : TapTarget()
    data object Waste : TapTarget()
    data class Tableau(val column: Int, val cardIndex: Int) : TapTarget()
    data class Foundation(val suit: Suit) : TapTarget()
}

data class CardPlacement(
    val x: Float,
    val y: Float,
    val z: Float,
    val faceUp: Boolean,
    val tap: TapTarget?,
)

fun computeBoardMetrics(
    widthPx: Float,
    heightPx: Float,
    density: Float,
    cardSize: CardSize,
    leftHanded: Boolean,
): BoardMetrics {
    val isLandscape = widthPx > heightPx
    val margin = 8f * density
    val gap = 5f * density
    val sizeScale = when (cardSize) {
        CardSize.NORMAL -> 1.0f
        CardSize.LARGE -> 1.12f
        CardSize.EXTRA_LARGE -> 1.25f
    }

    if (!isLandscape) {
        // Portrait: top row (stock, waste, fan space, 4 foundations), tableau below.
        val cardW = (widthPx - 2 * margin - 6 * gap) / 7f
        val cardH = cardW * 1.42f
        val topY = margin
        val slotX = { i: Int -> margin + i * (cardW + gap) }
        val mirror = { x: Float -> if (leftHanded) widthPx - x - cardW else x }

        val stock = Offset(mirror(slotX(0)), topY)
        val waste = Offset(mirror(slotX(1)), topY)
        val foundations = (0..3).map { f -> Offset(mirror(slotX(3 + f)), topY) }
        val fanDx = (cardW * 0.38f).let { if (leftHanded) -it else it }
        return BoardMetrics(
            boardW = widthPx, boardH = heightPx,
            cardW = cardW, cardH = cardH,
            stockPos = stock, wastePos = waste, wasteFanDx = fanDx,
            foundationPos = foundations,
            tableauX = (0..6).map { mirror(slotX(it)) },
            tableauTopY = topY + cardH + 10f * density,
            faceUpDy = cardH * (0.26f * sizeScale).coerceAtMost(0.34f),
            faceDownDy = cardH * 0.11f,
            indexScale = sizeScale,
            isLandscape = false,
        )
    } else {
        // Landscape: stock/waste on one side, foundations on the other,
        // tableau in the middle.
        val cardH = ((heightPx - 2 * margin) / 3.35f).coerceAtLeast(40f)
        var cardW = cardH / 1.42f
        val needed = 9 * cardW + 8 * gap + 2 * margin + 2 * gap
        if (needed > widthPx) cardW = (widthPx - 2 * margin - 10 * gap) / 9f
        val cardH2 = cardW * 1.42f
        val sideL = margin
        val sideR = widthPx - margin - cardW
        val leftX = if (leftHanded) sideR else sideL
        val rightX = if (leftHanded) sideL else sideR
        val tableauLeft = margin + cardW + 3 * gap
        val tableauWidth = widthPx - 2 * (margin + cardW + 3 * gap)
        val colGap = (tableauWidth - 7 * cardW) / 6f
        return BoardMetrics(
            boardW = widthPx, boardH = heightPx,
            cardW = cardW, cardH = cardH2,
            stockPos = Offset(leftX, margin),
            wastePos = Offset(leftX, margin + cardH2 + gap * 2),
            wasteFanDx = 0f, // landscape waste fans downward visually via z only
            foundationPos = (0..3).map { f -> Offset(rightX, margin + f * (cardH2 + gap)) },
            tableauX = (0..6).map { tableauLeft + it * (cardW + colGap) },
            tableauTopY = margin,
            faceUpDy = cardH2 * (0.26f * sizeScale).coerceAtMost(0.32f),
            faceDownDy = cardH2 * 0.10f,
            indexScale = sizeScale,
            isLandscape = true,
        )
    }
}

/**
 * Where every one of the 52 cards sits for [state]. Cards on foundations are
 * stacked in place (so a card animating up lands on its pile); z-order rises
 * toward the interactive top card of each pile.
 */
fun computePlacements(state: GameState, m: BoardMetrics): Map<Int, CardPlacement> {
    val out = HashMap<Int, CardPlacement>(52)

    // Stock: face-down stack with a hint of depth.
    state.stock.forEachIndexed { i, card ->
        val lift = (i / 8) * 1.5f
        out[card.id] = CardPlacement(
            x = m.stockPos.x - lift, y = m.stockPos.y - lift, z = i.toFloat(),
            faceUp = false,
            tap = TapTarget.Stock,
        )
    }

    // Waste: newest cards fan out; only the top card is interactive.
    val fanned = if (state.drawCount == 3) 3 else 1
    state.waste.forEachIndexed { i, card ->
        val fromTop = state.waste.size - 1 - i
        val fanIndex = (fanned - 1 - fromTop).coerceAtLeast(0)
        out[card.id] = CardPlacement(
            x = m.wastePos.x + fanIndex * m.wasteFanDx,
            y = m.wastePos.y,
            z = i.toFloat(),
            faceUp = true,
            tap = if (fromTop == 0) TapTarget.Waste else null,
        )
    }

    // Foundations: full stacks in place.
    for (suit in Suit.entries) {
        val count = state.foundations[suit.ordinal]
        val pos = m.foundationPos[suit.ordinal]
        for (rank in 1..count) {
            val id = suit.ordinal * 13 + (rank - 1)
            out[id] = CardPlacement(
                x = pos.x, y = pos.y, z = rank.toFloat(),
                faceUp = true,
                tap = if (rank == count) TapTarget.Foundation(suit) else null,
            )
        }
    }

    // Tableau columns, with fan compression so long piles stay on screen.
    state.tableau.forEachIndexed { col, pile ->
        val x = m.tableauX[col]
        val maxBottom = m.boardH - 4f
        var dyUp = m.faceUpDy
        val downCount = pile.faceDownCount
        val upCount = pile.cards.size - downCount
        val naturalHeight = downCount * m.faceDownDy + (upCount - 1).coerceAtLeast(0) * dyUp + m.cardH
        val available = maxBottom - m.tableauTopY
        if (naturalHeight > available && upCount > 1) {
            dyUp = ((available - m.cardH - downCount * m.faceDownDy) / (upCount - 1))
                .coerceAtLeast(m.cardH * 0.14f)
        }
        var y = m.tableauTopY
        pile.cards.forEachIndexed { idx, card ->
            out[card.id] = CardPlacement(
                x = x, y = y, z = (idx + 1).toFloat(),
                faceUp = idx >= downCount,
                tap = if (idx >= downCount) TapTarget.Tableau(col, idx) else null,
            )
            y += if (idx < downCount) m.faceDownDy else dyUp
        }
    }
    return out
}
