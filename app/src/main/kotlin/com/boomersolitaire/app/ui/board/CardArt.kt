package com.boomersolitaire.app.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import com.boomersolitaire.app.data.CardBack
import com.boomersolitaire.app.ui.theme.TableColors
import com.boomersolitaire.engine.Card
import com.boomersolitaire.engine.Suit

/**
 * All card faces and backs are drawn in code: crisp at any scale, themeable,
 * and the APK stays tiny.
 */
object CardArt {

    // ---- Suit shapes, defined in a unit box and scaled at draw time ----

    private val heartUnit: Path = Path().apply {
        moveTo(0.5f, 0.32f)
        cubicTo(0.5f, 0.12f, 0.34f, 0.02f, 0.2f, 0.02f)
        cubicTo(0.05f, 0.02f, 0f, 0.18f, 0f, 0.3f)
        cubicTo(0f, 0.55f, 0.25f, 0.72f, 0.5f, 0.98f)
        cubicTo(0.75f, 0.72f, 1f, 0.55f, 1f, 0.3f)
        cubicTo(1f, 0.18f, 0.95f, 0.02f, 0.8f, 0.02f)
        cubicTo(0.66f, 0.02f, 0.5f, 0.12f, 0.5f, 0.32f)
        close()
    }

    private val diamondUnit: Path = Path().apply {
        moveTo(0.5f, 0f)
        cubicTo(0.62f, 0.2f, 0.78f, 0.38f, 0.92f, 0.5f)
        cubicTo(0.78f, 0.62f, 0.62f, 0.8f, 0.5f, 1f)
        cubicTo(0.38f, 0.8f, 0.22f, 0.62f, 0.08f, 0.5f)
        cubicTo(0.22f, 0.38f, 0.38f, 0.2f, 0.5f, 0f)
        close()
    }

    private val spadeUnit: Path = Path().apply {
        moveTo(0.5f, 0.02f)
        cubicTo(0.28f, 0.28f, 0.04f, 0.38f, 0.04f, 0.58f)
        cubicTo(0.04f, 0.72f, 0.15f, 0.8f, 0.27f, 0.8f)
        cubicTo(0.35f, 0.8f, 0.43f, 0.76f, 0.465f, 0.69f)
        cubicTo(0.45f, 0.82f, 0.41f, 0.9f, 0.33f, 0.98f)
        lineTo(0.67f, 0.98f)
        cubicTo(0.59f, 0.9f, 0.55f, 0.82f, 0.535f, 0.69f)
        cubicTo(0.57f, 0.76f, 0.65f, 0.8f, 0.73f, 0.8f)
        cubicTo(0.85f, 0.8f, 0.96f, 0.72f, 0.96f, 0.58f)
        cubicTo(0.96f, 0.38f, 0.72f, 0.28f, 0.5f, 0.02f)
        close()
    }

    private val clubUnit: Path = Path().apply {
        addOval(Rect(Offset(0.5f - 0.21f, 0.06f), Size(0.42f, 0.42f)))
        addOval(Rect(Offset(0.06f, 0.36f), Size(0.42f, 0.42f)))
        addOval(Rect(Offset(0.52f, 0.36f), Size(0.42f, 0.42f)))
        moveTo(0.465f, 0.6f)
        cubicTo(0.45f, 0.78f, 0.41f, 0.88f, 0.33f, 0.98f)
        lineTo(0.67f, 0.98f)
        cubicTo(0.59f, 0.88f, 0.55f, 0.78f, 0.535f, 0.6f)
        close()
    }

    fun suitPath(suit: Suit): Path = when (suit) {
        Suit.SPADES -> spadeUnit
        Suit.HEARTS -> heartUnit
        Suit.DIAMONDS -> diamondUnit
        Suit.CLUBS -> clubUnit
    }

    /** Draw a suit symbol with its top-left corner at [topLeft], [size] px tall. */
    fun DrawScope.drawSuit(suit: Suit, topLeft: Offset, size: Float, color: Color, flipped: Boolean = false) {
        val path = Path().apply { addPath(suitPath(suit)) }
        val matrix = Matrix().apply {
            translate(topLeft.x, topLeft.y + if (flipped) size else 0f)
            scale(size, if (flipped) -size else size)
        }
        path.transform(matrix)
        drawPath(path, color)
    }

    // ---- Pip layouts for ranks 2..10 (x, y in card-relative units; flipped = drawn upside down) ----

    data class Pip(val x: Float, val y: Float, val flipped: Boolean = false)

    private val pipLayouts: Map<Int, List<Pip>> = mapOf(
        2 to listOf(Pip(0.5f, 0.22f), Pip(0.5f, 0.78f, true)),
        3 to listOf(Pip(0.5f, 0.2f), Pip(0.5f, 0.5f), Pip(0.5f, 0.8f, true)),
        4 to listOf(Pip(0.3f, 0.22f), Pip(0.7f, 0.22f), Pip(0.3f, 0.78f, true), Pip(0.7f, 0.78f, true)),
        5 to listOf(Pip(0.3f, 0.22f), Pip(0.7f, 0.22f), Pip(0.5f, 0.5f), Pip(0.3f, 0.78f, true), Pip(0.7f, 0.78f, true)),
        6 to listOf(
            Pip(0.3f, 0.22f), Pip(0.7f, 0.22f), Pip(0.3f, 0.5f), Pip(0.7f, 0.5f),
            Pip(0.3f, 0.78f, true), Pip(0.7f, 0.78f, true),
        ),
        7 to listOf(
            Pip(0.3f, 0.2f), Pip(0.7f, 0.2f), Pip(0.5f, 0.34f), Pip(0.3f, 0.5f), Pip(0.7f, 0.5f),
            Pip(0.3f, 0.8f, true), Pip(0.7f, 0.8f, true),
        ),
        8 to listOf(
            Pip(0.3f, 0.2f), Pip(0.7f, 0.2f), Pip(0.5f, 0.34f), Pip(0.3f, 0.5f), Pip(0.7f, 0.5f),
            Pip(0.5f, 0.66f, true), Pip(0.3f, 0.8f, true), Pip(0.7f, 0.8f, true),
        ),
        9 to listOf(
            Pip(0.3f, 0.18f), Pip(0.7f, 0.18f), Pip(0.3f, 0.4f), Pip(0.7f, 0.4f), Pip(0.5f, 0.5f),
            Pip(0.3f, 0.62f, true), Pip(0.7f, 0.62f, true), Pip(0.3f, 0.84f, true), Pip(0.7f, 0.84f, true),
        ),
        10 to listOf(
            Pip(0.3f, 0.18f), Pip(0.7f, 0.18f), Pip(0.5f, 0.28f), Pip(0.3f, 0.4f), Pip(0.7f, 0.4f),
            Pip(0.3f, 0.62f, true), Pip(0.7f, 0.62f, true), Pip(0.5f, 0.73f, true), Pip(0.3f, 0.84f, true), Pip(0.7f, 0.84f, true),
        ),
    )

    /**
     * Draw the middle of a card face (pips, ace, or court figure) inside
     * [w] x [h], assuming the corner indices occupy the outer margins.
     * [indexScale] is how much larger than normal the corner indices are,
     * so the court panel can stay clear of them.
     */
    fun DrawScope.drawFaceCenter(card: Card, w: Float, h: Float, suitColor: Color, accent: Color, indexScale: Float = 1f) {
        val rank = card.rank
        when {
            rank == 1 -> {
                val s = w * 0.42f
                drawSuit(card.suit, Offset((w - s) / 2f, (h - s) / 2f), s, suitColor)
            }
            rank in 2..10 -> {
                // Pips live in an inner window that clears the corner marks —
                // a wide rank like "10" plus a big index must never be clipped
                // by the top pip row.
                val left = w * 0.08f
                val innerW = w * 0.84f
                val top = maxOf(h * 0.14f, w * (0.06f + 0.34f * indexScale))
                val innerH = h * 0.88f - top
                val pip = w * 0.19f
                for (p in pipLayouts.getValue(rank)) {
                    drawSuit(
                        card.suit,
                        Offset(left + p.x * innerW - pip / 2f, top + p.y * innerH - pip / 2f),
                        pip,
                        suitColor,
                        flipped = p.flipped,
                    )
                }
            }
            else -> drawCourt(card, w, h, suitColor, accent, indexScale)
        }
    }

    /** Clean geometric court cards: a framed panel with a crown/tiara/plume. */
    private fun DrawScope.drawCourt(card: Card, w: Float, h: Float, suitColor: Color, accent: Color, indexScale: Float) {
        // The panel's top edge stays below the corner rank and suit marks,
        // whatever size the indices are drawn at.
        val panelTop = (w * (0.07f + 0.21f * indexScale + 0.045f)).coerceAtLeast(h * 0.14f)
        val panel = Rect(w * 0.22f, panelTop, w * 0.78f, h * 0.86f)
        val corner = w * 0.045f
        // Panel frame.
        drawRoundRect(
            color = accent,
            topLeft = panel.topLeft,
            size = panel.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
            style = Stroke(width = w * 0.02f),
        )
        val cx = panel.center.x
        val third = panel.height / 3f

        // Motif in the upper third.
        val motifW = panel.width * 0.62f
        val motifH = third * 0.52f
        val motifTop = panel.top + third * 0.28f
        when (card.rank) {
            13 -> { // King: a solid five-point crown
                val p = Path().apply {
                    moveTo(cx - motifW / 2, motifTop + motifH)
                    lineTo(cx - motifW / 2, motifTop + motifH * 0.35f)
                    lineTo(cx - motifW * 0.25f, motifTop + motifH * 0.65f)
                    lineTo(cx, motifTop)
                    lineTo(cx + motifW * 0.25f, motifTop + motifH * 0.65f)
                    lineTo(cx + motifW / 2, motifTop + motifH * 0.35f)
                    lineTo(cx + motifW / 2, motifTop + motifH)
                    close()
                }
                drawPath(p, accent)
            }
            12 -> { // Queen: a tiara of three circles on an arc
                val r = motifH * 0.24f
                val baseY = motifTop + motifH * 0.72f
                drawCircle(accent, r, Offset(cx - motifW * 0.32f, baseY))
                drawCircle(accent, r * 1.3f, Offset(cx, motifTop + motifH * 0.42f))
                drawCircle(accent, r, Offset(cx + motifW * 0.32f, baseY))
                drawRoundRect(
                    accent,
                    topLeft = Offset(cx - motifW / 2, baseY + r * 0.6f),
                    size = Size(motifW, motifH * 0.16f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(motifH * 0.08f),
                )
            }
            else -> { // Jack: a pennant on a staff
                val staffX = cx - motifW * 0.3f
                drawRoundRect(
                    accent,
                    topLeft = Offset(staffX - w * 0.012f, motifTop),
                    size = Size(w * 0.024f, motifH * 1.15f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.012f),
                )
                val p = Path().apply {
                    moveTo(staffX + w * 0.02f, motifTop)
                    lineTo(staffX + w * 0.02f + motifW * 0.62f, motifTop + motifH * 0.28f)
                    lineTo(staffX + w * 0.02f, motifTop + motifH * 0.56f)
                    close()
                }
                drawPath(p, accent)
            }
        }

        // Large suit pip in the middle third, mirrored below.
        val pip = panel.width * 0.4f
        drawSuit(card.suit, Offset(cx - pip / 2f, panel.top + third * 1.18f), pip, suitColor)
        drawSuit(
            card.suit,
            Offset(cx - pip / 2f, panel.top + third * 2.3f),
            pip * 0.62f,
            suitColor,
            flipped = true,
        )
    }

    // ---- Card backs ----

    fun DrawScope.drawCardBack(style: CardBack, w: Float, h: Float, table: TableColors) {
        val base = table.cardBack
        val accent = table.cardBackAccent
        val inset = w * 0.07f
        val innerW = w - 2 * inset
        val innerH = h - 2 * inset
        // Border frame.
        drawRoundRect(
            color = accent.copy(alpha = 0.85f),
            topLeft = Offset(inset, inset),
            size = Size(innerW, innerH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
            style = Stroke(width = w * 0.025f),
        )
        val clip = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    Rect(inset + w * 0.02f, inset + w * 0.02f, w - inset - w * 0.02f, h - inset - w * 0.02f),
                    androidx.compose.ui.geometry.CornerRadius(w * 0.04f),
                ),
            )
        }
        clipPath(clip) {
            when (style) {
                CardBack.STRIPES -> {
                    val stripe = w * 0.09f
                    var x = -h * 0.6f
                    while (x < w) {
                        drawLine(
                            accent.copy(alpha = 0.5f),
                            start = Offset(x, h),
                            end = Offset(x + h * 0.6f, 0f),
                            strokeWidth = stripe * 0.45f,
                        )
                        x += stripe
                    }
                }
                CardBack.LATTICE -> {
                    val step = w * 0.14f
                    var d = -h
                    while (d < w + h) {
                        drawLine(accent.copy(alpha = 0.45f), Offset(d, 0f), Offset(d + h, h), strokeWidth = w * 0.015f)
                        drawLine(accent.copy(alpha = 0.45f), Offset(d + h, 0f), Offset(d, h), strokeWidth = w * 0.015f)
                        d += step
                    }
                }
                CardBack.SUNBURST -> {
                    val c = Offset(w / 2f, h / 2f)
                    for (i in 0 until 12) {
                        rotate(degrees = i * 30f, pivot = c) {
                            drawLine(
                                accent.copy(alpha = 0.5f),
                                start = c,
                                end = Offset(w / 2f, -h * 0.2f),
                                strokeWidth = w * 0.05f,
                            )
                        }
                    }
                    drawCircle(accent.copy(alpha = 0.9f), radius = w * 0.13f, center = c)
                    drawCircle(base, radius = w * 0.08f, center = c)
                }
            }
        }
    }

}
