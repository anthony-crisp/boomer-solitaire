package com.boomersolitaire.app.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand-drawn icons for the in-game bar, in the same drawn language as the
 * cards — no emoji, no icon font. Each draws inside the DrawScope's bounds.
 */
enum class BarIcon { UNDO, HINT, DRAW }

fun DrawScope.drawBarIcon(icon: BarIcon, color: Color) {
    when (icon) {
        BarIcon.UNDO -> drawUndoArrow(color)
        BarIcon.HINT -> drawSparkle(color)
        BarIcon.DRAW -> drawCardBackIcon(color)
    }
}

/** A circular arrow sweeping back on itself. */
private fun DrawScope.drawUndoArrow(color: Color) {
    val s = size.minDimension
    val stroke = s * 0.11f
    val r = s * 0.34f
    val c = Offset(size.width / 2f, size.height / 2f + s * 0.02f)
    // Arc from 300 deg sweeping 250 deg clockwise, leaving a gap where the
    // arrowhead sits.
    drawArc(
        color = color,
        startAngle = -60f,
        sweepAngle = 250f,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(2 * r, 2 * r),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    // Arrowhead at the arc's start point, pointing along the tangent.
    val angle = Math.toRadians(-60.0)
    val tip = Offset(c.x + r * cos(angle).toFloat(), c.y + r * sin(angle).toFloat())
    val head = Path().apply {
        moveTo(tip.x + s * 0.16f, tip.y - s * 0.04f)
        lineTo(tip.x - s * 0.10f, tip.y - s * 0.14f)
        lineTo(tip.x - s * 0.02f, tip.y + s * 0.16f)
        close()
    }
    drawPath(head, color)
}

/** A gentle four-point sparkle. */
private fun DrawScope.drawSparkle(color: Color) {
    val s = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = s * 0.46f
    val waist = s * 0.10f
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x + waist, c.y - waist, c.x + r, c.y)
        quadraticTo(c.x + waist, c.y + waist, c.x, c.y + r)
        quadraticTo(c.x - waist, c.y + waist, c.x - r, c.y)
        quadraticTo(c.x - waist, c.y - waist, c.x, c.y - r)
        close()
    }
    drawPath(path, color)
    drawCircle(color.copy(alpha = 0.55f), radius = s * 0.06f, center = Offset(c.x + r * 0.62f, c.y - r * 0.7f))
}

/** A tilted little card back with stripes. */
private fun DrawScope.drawCardBackIcon(color: Color) {
    val s = size.minDimension
    val w = s * 0.58f
    val h = w * 1.42f
    val topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f)
    val stroke = s * 0.09f
    rotate(degrees = -8f, pivot = Offset(size.width / 2f, size.height / 2f)) {
        drawRoundRect(
            color = color,
            topLeft = topLeft,
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.18f),
            style = Stroke(width = stroke),
        )
        val inset = w * 0.24f
        val rect = Rect(topLeft.x + inset, topLeft.y + inset, topLeft.x + w - inset, topLeft.y + h - inset)
        drawLine(color, Offset(rect.left, rect.bottom), Offset(rect.right, rect.top), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
    }
}
