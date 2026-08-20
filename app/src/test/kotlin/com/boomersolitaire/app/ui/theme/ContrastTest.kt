package com.boomersolitaire.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Every colour the app puts text on must be legible in every table style.
 *
 * This exists because it was got wrong: AlertDialog's body text uses the
 * `onSurfaceVariant` role, which the theme never set, so it inherited
 * Material's default grey and rendered at 1.21:1 on the green felt — the
 * default table on a light-mode phone. A prose note saying "keep this
 * readable" would not have caught it; this does.
 */
class ContrastTest {

    private fun relativeLuminance(rgb: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = channel((rgb shr 16) and 0xFF)
        val g = channel((rgb shr 8) and 0xFF)
        val b = channel(rgb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun ratio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** One table style's foreground/background roles, as actually themed. */
    private class Palette(
        val name: String,
        val background: Int,
        val surface: Int,
        val onBackground: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val primary: Int,
        val onPrimary: Int,
        val outline: Int,
    )

    private val palettes = listOf(
        // Green felt — also what AUTO resolves to on a light-mode phone.
        Palette(
            "felt", 0x1B5E43, 0x124232, 0xFDFBF4, 0xFDFBF4, 0xDDD6C4,
            0xE0B463, 0x241A05, 0x94B6A2,
        ),
        Palette(
            "linen", 0xE9E2D0, 0xF4EEDF, 0x35301F, 0x35301F, 0x574F3C,
            0x6A4310, 0xFFF6E4, 0x7F775F,
        ),
        Palette(
            "dark", 0x16211C, 0x0D1512, 0xF3EFE4, 0xF3EFE4, 0xD5CFC1,
            0xE0B463, 0x241A05, 0x74847B,
        ),
        Palette(
            "highContrast", 0x000000, 0x000000, 0xF3EFE4, 0xF3EFE4, 0xFFFFFF,
            0xE0B463, 0x241A05, 0xFFFFFF,
        ),
    )

    @Test
    fun `body text is legible on every surface in every table style`() {
        for (p in palettes) {
            // WCAG AA for normal-size text.
            check(p.name, "onBackground on background", ratio(p.onBackground, p.background), 4.5)
            check(p.name, "onSurface on surface", ratio(p.onSurface, p.surface), 4.5)
            check(p.name, "onSurfaceVariant on surface", ratio(p.onSurfaceVariant, p.surface), 4.5)
            check(p.name, "onSurfaceVariant on background", ratio(p.onSurfaceVariant, p.background), 4.5)
            // Small primary-coloured text (card headings) only ever sits on a
            // glass panel, i.e. `surface`, so it must clear the full 4.5.
            check(p.name, "primary on surface", ratio(p.primary, p.surface), 4.5)
            // Directly on the table, primary is only used for the 46sp title
            // and the spinner, where WCAG's large-text/graphics bar applies.
            check(p.name, "primary on background", ratio(p.primary, p.background), 3.0)
            check(p.name, "onPrimary on primary", ratio(p.onPrimary, p.primary), 4.5)
        }
    }

    @Test
    fun `outlines are visible on every surface in every table style`() {
        for (p in palettes) {
            // WCAG AA for non-text UI components such as borders.
            check(p.name, "outline on surface", ratio(p.outline, p.surface), 3.0)
            check(p.name, "outline on background", ratio(p.outline, p.background), 3.0)
        }
    }

    private fun check(palette: String, pair: String, actual: Double, required: Double) {
        assertTrue(
            "$palette: $pair is %.2f:1, needs %.1f:1".format(actual, required),
            actual >= required,
        )
    }
}
