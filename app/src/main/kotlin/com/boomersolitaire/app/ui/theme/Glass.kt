package com.boomersolitaire.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * The furniture material: menus, dialogs, sheets, empty pile slots.
 * Card faces are NEVER glass — a card is an opaque plate whose legibility
 * must not depend on what sits behind it.
 *
 * Every tier is a black scrim first and a small white sheen second, so
 * thicker glass gets darker and MORE readable over bright content, never
 * less. (On the one light table — linen — the roles invert: white scrim,
 * dark rim.)
 */
enum class GlassTier(
    val scrim: Float,
    val sheen: Float,
    val rimTop: Float,
    val rimBottom: Float,
) {
    /** Controls and slots sitting directly on the table. */
    VEIL(0.40f, 0.06f, 0.22f, 0.06f),

    /** Cards, panels, toasts. */
    RAISED(0.45f, 0.08f, 0.28f, 0.08f),

    /** Dialogs and sheets. */
    TRANSIENT(0.78f, 0.08f, 0.36f, 0.10f),
}

/** A 128px fixed-seed noise tile; one texture for the whole app. */
private val noiseBitmap: ImageBitmap by lazy {
    val size = 128
    val rng = Random(42)
    val pixels = IntArray(size * size) {
        val v = rng.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

fun grainBrush(): ShaderBrush =
    ShaderBrush(ImageShader(noiseBitmap, TileMode.Repeated, TileMode.Repeated))

/**
 * Scrim -> sheen -> inner top light -> inner bottom line -> grain -> rim,
 * drawn behind the content and cached. [pressed] is read in the draw phase
 * so a press never recomposes the subtree.
 */
fun Modifier.glass(
    shape: Shape,
    tier: GlassTier,
    lightTable: Boolean = false,
    pressed: (() -> Boolean)? = null,
): Modifier = this
    .clip(shape)
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply {
            when (outline) {
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Generic -> addPath(outline.path)
            }
        }
        val grain = grainBrush()
        val stroke = Stroke(1.dp.toPx())
        onDrawBehind {
            val bump = if (pressed?.invoke() == true) 0.04f else 0f
            val scrimColor = if (lightTable) Color.White else Color.Black
            val sheenColor = if (lightTable) Color.Black else Color.White
            clipPath(path) {
                drawRect(scrimColor.copy(alpha = (tier.scrim + if (lightTable) 0.25f else 0f).coerceAtMost(1f)))
                drawRect(sheenColor.copy(alpha = tier.sheen * 0.5f + bump))
                // Inner top light: the only stand-in for an inset shadow —
                // this is what makes the surface read as having thickness.
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.045f + bump),
                        1f to Color.Transparent,
                        endY = size.height * 0.4f,
                    ),
                    size = size.copy(height = size.height * 0.4f),
                )
                // Inner bottom line.
                drawRect(
                    color = Color.Black.copy(alpha = 0.12f),
                    topLeft = Offset(0f, size.height - 1.dp.toPx()),
                    size = size.copy(height = 1.dp.toPx()),
                )
                drawRect(grain, alpha = 0.05f)
            }
            // Rim: vertical gradient, never flat — glass catches light on top.
            val rimColor = if (lightTable) Color.Black else Color.White
            drawPath(
                path,
                brush = Brush.verticalGradient(
                    0f to rimColor.copy(alpha = tier.rimTop * (if (lightTable) 0.6f else 1f) + bump),
                    1f to rimColor.copy(alpha = tier.rimBottom * (if (lightTable) 0.6f else 1f)),
                ),
                style = stroke,
            )
        }
    }

/**
 * The table itself: a soft radial light pooled above centre, the theme's
 * felt falling to its shadow at the edges, with grain to kill banding.
 */
fun Modifier.feltBackground(table: TableColors): Modifier = drawWithCache {
    val lightened = lerp(table.felt, Color.White, if (table.isDark) 0.045f else 0.06f)
    val brush = Brush.radialGradient(
        0f to lightened,
        0.55f to table.felt,
        1f to table.feltShadow,
        center = Offset(size.width / 2f, size.height * 0.38f),
        radius = maxOf(size.width, size.height) * 0.85f,
    )
    val grain = grainBrush()
    onDrawBehind {
        drawRect(brush)
        drawRect(grain, alpha = 0.035f)
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/** Whether the active table needs the light-glass variant. */
@Composable
fun rememberLightTable(): Boolean {
    val table = LocalTableColors.current
    return remember(table) {
        // Perceptual-ish luminance of the felt decides the glass polarity.
        val l = 0.2126f * table.felt.red + 0.7152f * table.felt.green + 0.0722f * table.felt.blue
        l > 0.5f
    }
}
