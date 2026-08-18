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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import com.boomersolitaire.app.ui.theme.LocalTableColors
import com.boomersolitaire.engine.Suit
import androidx.compose.runtime.withFrameNanos
import kotlin.math.min
import kotlin.random.Random

private class Flake(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rot: Float,
    var vr: Float,
    val suit: Suit,
    val faceUp: Boolean,
    var alpha: Float = 1f,
    var bounces: Int = 0,
)

/**
 * The classic cascading-cards moment, reimagined gently: a shower of little
 * cards tumbles and bounces once or twice, then fades. Skipped entirely when
 * reduce-motion is on.
 */
@Composable
fun WinCelebration(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    if (reduceMotion) return
    val table = LocalTableColors.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val cardW = w / 9f
        val cardH = cardW * 1.42f

        val flakes = remember { mutableListOf<Flake>() }
        var frame by mutableLongStateOf(0L)

        LaunchedEffect(Unit) {
            val rng = Random(System.currentTimeMillis())
            var spawned = 0
            var last = System.nanoTime()
            val start = last
            while (true) {
                withFrameNanos { }
                val now = System.nanoTime()
                val dt = min((now - last) / 1_000_000_000f, 0.032f)
                last = now
                val elapsed = (now - start) / 1_000_000_000f

                // Spawn a new card every few frames for the first ~2.5s.
                if (elapsed < 2.5f && spawned < 26 && flakes.size < 26 && rng.nextFloat() < 0.35f) {
                    flakes.add(
                        Flake(
                            x = rng.nextFloat() * (w - cardW),
                            y = -cardH - rng.nextFloat() * h * 0.2f,
                            vx = (rng.nextFloat() - 0.5f) * w * 0.35f,
                            vy = rng.nextFloat() * h * 0.15f,
                            rot = rng.nextFloat() * 360f,
                            vr = (rng.nextFloat() - 0.5f) * 240f,
                            suit = Suit.entries[rng.nextInt(4)],
                            faceUp = rng.nextBoolean(),
                        ),
                    )
                    spawned++
                }

                val gravity = h * 0.9f
                val floor = h - cardH * 0.6f
                val it2 = flakes.iterator()
                while (it2.hasNext()) {
                    val f = it2.next()
                    f.vy += gravity * dt
                    f.x += f.vx * dt
                    f.y += f.vy * dt
                    f.rot += f.vr * dt
                    if (f.y > floor && f.vy > 0) {
                        f.bounces++
                        f.vy = -f.vy * 0.5f
                        f.vx *= 0.8f
                        f.vr *= 0.7f
                        f.y = floor
                    }
                    if (f.bounces >= 2) f.alpha -= dt * 1.4f
                    if (elapsed > 5f) f.alpha -= dt * 1.2f
                    if (f.alpha <= 0f) it2.remove()
                }
                frame++
                if (elapsed > 3f && flakes.isEmpty()) break
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") frame // invalidate every physics frame
            for (f in flakes) {
                val a = f.alpha.coerceIn(0f, 1f)
                rotate(f.rot, pivot = Offset(f.x + cardW / 2f, f.y + cardH / 2f)) {
                    if (f.faceUp) {
                        drawRoundRect(
                            color = table.cardFace.copy(alpha = a),
                            topLeft = Offset(f.x, f.y),
                            size = Size(cardW, cardH),
                            cornerRadius = CornerRadius(cardW * 0.09f),
                        )
                        val suitColor = (if (f.suit.isRed) table.red else table.black).copy(alpha = a)
                        with(CardArt) {
                            drawSuit(f.suit, Offset(f.x + cardW * 0.25f, f.y + cardH * 0.28f), cardW * 0.5f, suitColor)
                        }
                    } else {
                        drawRoundRect(
                            color = table.cardBack.copy(alpha = a),
                            topLeft = Offset(f.x, f.y),
                            size = Size(cardW, cardH),
                            cornerRadius = CornerRadius(cardW * 0.09f),
                        )
                        drawRoundRect(
                            color = table.cardBackAccent.copy(alpha = a * 0.7f),
                            topLeft = Offset(f.x + cardW * 0.08f, f.y + cardW * 0.08f),
                            size = Size(cardW * 0.84f, cardH - cardW * 0.16f),
                            cornerRadius = CornerRadius(cardW * 0.06f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(cardW * 0.03f),
                        )
                    }
                }
            }
        }
    }
}
