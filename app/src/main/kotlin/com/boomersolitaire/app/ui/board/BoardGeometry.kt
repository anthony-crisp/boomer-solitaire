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
    /** Order this card is dealt in at game start (tableau cards only). */
    val dealOrder: Int? = null,
)

fun computeBoardMetrics(
    widthPx: Float,
    heightPx: Float,
    density: Float,
    cardSize: CardSize,
    leftHanded: Boolean,
): BoardMetrics {
    val isLandscape = widthPx > heightPx
    // Seven columns pin the card width in portrait, so "bigger cards" is
    // delivered honestly: tighter margins, a taller card, larger indices,
    // and a roomier fan — not a scale factor that changes nothing.
    val sizeScale = when (cardSize) {
        CardSize.NORMAL -> 1.0f
        CardSize.LARGE -> 1.14f
        CardSize.EXTRA_LARGE -> 1.28f
    }
    val aspect = when (cardSize) {
        CardSize.NORMAL -> 1.42f
        CardSize.LARGE -> 1.5f
        CardSize.EXTRA_LARGE -> 1.58f
    }
    val margin = when (cardSize) {
        CardSize.NORMAL -> 8f
        CardSize.LARGE -> 6f
        CardSize.EXTRA_LARGE -> 4f
    } * density
    val gap = when (cardSize) {
        CardSize.NORMAL -> 5f
        CardSize.LARGE -> 4f
        CardSize.EXTRA_LARGE -> 3f
    } * density

    if (!isLandscape) {
        // Portrait: top row (stock, waste, fan space, 4 foundations), tableau below.
        val cardW = (widthPx - 2 * margin - 6 * gap) / 7f
        val cardH = cardW * aspect
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
        // Landscape: stock/waste on one side, a 2x2 foundation grid on the
        // other, tableau in the middle. Bigger sizes claim more of the height.
        val heightDivisor = when (cardSize) {
            CardSize.NORMAL -> 3.35f
            CardSize.LARGE -> 3.1f
            CardSize.EXTRA_LARGE -> 2.9f
        }
        val cardH = ((heightPx - 2 * margin) / heightDivisor).coerceAtLeast(40f)
        var cardW = cardH / aspect
        val needed = 10 * cardW + 12 * gap + 2 * margin
        if (needed > widthPx) cardW = (widthPx - 2 * margin - 12 * gap) / 10f
        val cardH2 = cardW * aspect
        val mirror = { x: Float -> if (leftHanded) widthPx - x - cardW else x }
        val leftX = mirror(margin)
        val rightInner = widthPx - margin - cardW // right-most column
        val rightOuter = rightInner - cardW - gap
        val f1x = mirror(rightOuter)
        val f2x = mirror(rightInner)
        val tableauLeft = margin + cardW + 3 * gap
        val tableauRight = rightOuter - 3 * gap
        val tableauWidth = tableauRight - tableauLeft
        val colGap = (tableauWidth - 7 * cardW) / 6f
        val tableauXs = (0..6).map { tableauLeft + it * (cardW + colGap) }
        return BoardMetrics(
            boardW = widthPx, boardH = heightPx,
            cardW = cardW, cardH = cardH2,
            stockPos = Offset(leftX, margin),
            wastePos = Offset(leftX, margin + cardH2 + gap * 2),
            wasteFanDx = 0f, // landscape waste stacks; only the top is shown
            foundationPos = listOf(
                Offset(f1x, margin),                      // spades
                Offset(f2x, margin),                      // hearts
                Offset(f1x, margin + cardH2 + gap),       // diamonds
                Offset(f2x, margin + cardH2 + gap),       // clubs
            ),
            tableauX = if (leftHanded) tableauXs.map { mirror(it) }.reversed() else tableauXs,
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

    // Stock: cards sit flush — sub-radius offsets would poke through the
    // top card's corner curve and make it look square.
    state.stock.forEachIndexed { i, card ->
        out[card.id] = CardPlacement(
            x = m.stockPos.x, y = m.stockPos.y, z = i.toFloat(),
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
            // Compress to fit. The old floor here raised the pitch back above
            // the fitting value, so a long column in landscape at a large card
            // size overflowed behind the button bar. Fit wins; a very long run
            // simply gets a tighter fan.
            dyUp = ((available - m.cardH - downCount * m.faceDownDy) / (upCount - 1))
                .coerceAtLeast(m.cardH * 0.055f)
        }
        var y = m.tableauTopY
        pile.cards.forEachIndexed { idx, card ->
            // Deal order: round idx deals one card to each of columns idx..6.
            val dealOrder = (0 until idx).sumOf { 7 - it } + (col - idx)
            out[card.id] = CardPlacement(
                x = x, y = y, z = (idx + 1).toFloat(),
                faceUp = idx >= downCount,
                tap = if (idx >= downCount) TapTarget.Tableau(col, idx) else null,
                dealOrder = if (idx <= col) dealOrder else null,
            )
            y += if (idx < downCount) m.faceDownDy else dyUp
        }
    }
    return out
}
