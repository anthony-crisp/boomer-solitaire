package com.boomersolitaire.app.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import com.boomersolitaire.app.data.Settings
import com.boomersolitaire.app.ui.theme.LocalTableColors
import com.boomersolitaire.engine.Card
import com.boomersolitaire.engine.Suit
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The Windows 3.0 victory cascade, done properly: each foundation fires its
 * cards one after another; every card falls, bounces along the bottom of the
 * screen, and — the famous part — leaves a permanent trail behind it.
 *
 * The original trail was an accident (the screen was simply never repainted
 * between frames); here it is deliberate: each frame stamps the card into an
 * offscreen bitmap that is never cleared, drawn over the board.
 *
 * Skipped entirely when reduce-motion is on.
 */
@Composable
fun WinCelebration(settings: Settings, modifier: Modifier = Modifier) {
    if (settings.reduceMotion) return
    val table = LocalTableColors.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        if (w < 1f || h < 1f) return@BoxWithConstraints
        val metrics = remember(w, h, settings.cardSize, settings.leftHanded) {
            computeBoardMetrics(w, h, density.density, settings.cardSize, settings.leftHanded)
        }
        val cardW = metrics.cardW
        val cardH = metrics.cardH

        val trail = remember(w, h) { ImageBitmap(w.roundToInt(), h.roundToInt()) }
        val trailCanvas = remember(trail) { androidx.compose.ui.graphics.Canvas(trail) }
        val stampScope = remember { CanvasDrawScope() }
        var frame by remember { mutableLongStateOf(0L) }

        fun stamp(card: Card, x: Float, y: Float) {
            stampScope.draw(density, LayoutDirection.Ltr, trailCanvas, Size(w, h)) {
                translate(x, y) {
                    val corner = CornerRadius(cardW * 0.09f)
                    drawRoundRect(table.cardFace, Offset.Zero, Size(cardW, cardH), corner)
                    drawRoundRect(
                        table.cardEdge, Offset.Zero, Size(cardW, cardH), corner,
                        style = Stroke(width = density.density),
                    )
                    val suitColor = when {
                        !settings.fourColorDeck -> if (card.isRed) table.red else table.black
                        else -> when (card.suit) {
                            Suit.SPADES -> table.black
                            Suit.HEARTS -> table.red
                            Suit.DIAMONDS -> table.blue
                            Suit.CLUBS -> table.green
                        }
                    }
                    with(CardArt) {
                        drawFaceCenter(card, cardW, cardH, suitColor, table.courtAccent, metrics.indexScale)
                        val s = cardW * 0.21f * metrics.indexScale
                        drawSuit(card.suit, Offset(cardW - s - cardW * 0.06f, cardW * 0.07f), s, suitColor)
                    }
                    drawText(
                        textMeasurer,
                        text = rankLabel(card.rank),
                        topLeft = Offset(cardW * 0.06f, 0f),
                        style = TextStyle(
                            color = suitColor,
                            fontSize = with(density) { (cardW * 0.30f * metrics.indexScale).toSp() },
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                        ),
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            val rng = Random(System.nanoTime())
            // Pop the top card of each foundation in rotation: K K K K Q Q...
            val piles = Suit.entries.map { s -> (13 downTo 1).map { Card.of(s, it) }.toMutableList() }
            val launchOrder = buildList {
                var i = 0
                while (piles.any { it.isNotEmpty() }) {
                    val pile = piles[i % 4]
                    if (pile.isNotEmpty()) add(Suit.entries[i % 4] to pile.removeAt(0))
                    i++
                }
            }

            class Flying(
                val card: Card,
                var x: Float, var y: Float,
                var vx: Float, var vy: Float,
                var bounces: Int = 0,
                var sinceStamp: Float = 999f,
            )

            val active = mutableListOf<Flying>()
            var next = 0
            var sinceLaunch = 999f
            val gravity = h * 3.4f
            val floor = h - cardH
            var last = withFrameNanos { it }

            while (next < launchOrder.size || active.isNotEmpty()) {
                val now = withFrameNanos { it }
                val dt = min((now - last) / 1_000_000_000f, 0.032f)
                last = now
                sinceLaunch += dt

                // The next card leaves its pile once the current one has hit
                // the floor (or shortly after) — near-sequential, never stalls.
                val current = active.lastOrNull()
                if (next < launchOrder.size &&
                    (current == null || current.bounces >= 1 || sinceLaunch > 0.9f)
                ) {
                    val (suit, card) = launchOrder[next++]
                    val start = metrics.foundationPos[suit.ordinal]
                    active.add(
                        Flying(
                            card = card,
                            x = start.x,
                            y = start.y,
                            vx = (0.35f + 0.55f * rng.nextFloat()) * w * (if (rng.nextBoolean()) 1f else -1f),
                            vy = -rng.nextFloat() * h * 0.15f,
                        ),
                    )
                    sinceLaunch = 0f
                }

                val iterator = active.iterator()
                while (iterator.hasNext()) {
                    val f = iterator.next()
                    f.vy += gravity * dt
                    val stepX = f.vx * dt
                    val stepY = f.vy * dt
                    f.x += stepX
                    f.y += stepY
                    if (f.y > floor) {
                        f.y = floor
                        f.vy = -f.vy * 0.72f
                        f.bounces++
                    }
                    // The 1990 original ran at a low frame rate, so its trail
                    // was discrete overlapping copies — stamp by distance
                    // travelled, not by frame, to match that look.
                    f.sinceStamp += kotlin.math.sqrt(stepX * stepX + stepY * stepY)
                    if (f.sinceStamp >= cardW * 0.3f) {
                        stamp(f.card, f.x, f.y)
                        f.sinceStamp = 0f
                    }
                    if (f.x < -cardW || f.x > w) iterator.remove()
                }
                frame++
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") frame // redraw as the trail grows
            drawImage(trail)
        }
    }
}
