package com.boomersolitaire.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.boomersolitaire.app.data.AppTheme

/** Colours specific to the card table, beyond Material's scheme. */
data class TableColors(
    val felt: Color,
    val feltShadow: Color,
    val cardFace: Color,
    val cardEdge: Color,
    val cardBack: Color,
    val cardBackAccent: Color,
    val pileOutline: Color,
    val black: Color,
    val red: Color,
    val blue: Color,   // four-colour deck: diamonds
    val green: Color,  // four-colour deck: clubs
    val highlight: Color,
    val isDark: Boolean,
)

val LocalTableColors = staticCompositionLocalOf { feltTable }

private val feltTable = TableColors(
    felt = Color(0xFF1B5E43),
    feltShadow = Color(0xFF124232),
    cardFace = Color(0xFFFDFBF4),
    cardEdge = Color(0xFFCBC5B5),
    cardBack = Color(0xFF9C3D2E),
    cardBackAccent = Color(0xFFE8D9BC),
    pileOutline = Color(0x66FFFFFF),
    black = Color(0xFF23261F),
    red = Color(0xFFB3352C),
    blue = Color(0xFF1F5CA8),
    green = Color(0xFF1E6B3C),
    highlight = Color(0xFFF2C464),
    isDark = false,
)

private val linenTable = feltTable.copy(
    felt = Color(0xFFE9E2D0),
    feltShadow = Color(0xFFD6CDB6),
    pileOutline = Color(0x59453F30),
    cardFace = Color(0xFFFFFFFF),
    cardEdge = Color(0xFFB9B2A0),
    cardBack = Color(0xFF3E6B8C),
    highlight = Color(0xFFC96F2E),
)

private val darkTable = feltTable.copy(
    felt = Color(0xFF16211C),
    feltShadow = Color(0xFF0D1512),
    cardFace = Color(0xFFE8E4D8),
    cardEdge = Color(0xFF4A4940),
    cardBack = Color(0xFF37474F),
    pileOutline = Color(0x4DFFFFFF),
    highlight = Color(0xFFE8B455),
    isDark = true,
)

private val highContrastTable = feltTable.copy(
    felt = Color(0xFF000000),
    feltShadow = Color(0xFF000000),
    cardFace = Color(0xFFFFFFFF),
    cardEdge = Color(0xFF000000),
    cardBack = Color(0xFF0033AA),
    pileOutline = Color(0xB3FFFFFF),
    black = Color(0xFF000000),
    red = Color(0xFFC80000),
    blue = Color(0xFF0000E0),
    green = Color(0xFF006400),
    highlight = Color(0xFFFFD700),
    isDark = true,
)

@Composable
fun BoomerTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val table = when (theme) {
        AppTheme.AUTO -> if (systemDark) darkTable else feltTable
        AppTheme.FELT -> feltTable
        AppTheme.LINEN -> linenTable
        AppTheme.DARK -> darkTable
        AppTheme.HIGH_CONTRAST -> highContrastTable
    }
    val scheme = when {
        table.isDark -> darkColorScheme(
            primary = Color(0xFFE0B463),
            onPrimary = Color(0xFF241A05),
            secondary = Color(0xFF9CC7AD),
            background = table.felt,
            surface = table.feltShadow,
            onBackground = Color(0xFFF3EFE4),
            onSurface = Color(0xFFF3EFE4),
        )
        theme == AppTheme.LINEN -> lightColorScheme(
            // A light table needs dark ink — measured, not inherited:
            // white-on-linen is 1.25:1.
            primary = Color(0xFF6A4310),
            onPrimary = Color(0xFFFFF6E4),
            secondary = Color(0xFF3D6B52),
            background = table.felt,
            surface = Color(0xFFF4EEDF),
            onBackground = Color(0xFF35301F),
            onSurface = Color(0xFF35301F),
        )
        else -> lightColorScheme(
            // The green felt is dark despite being the "light" scheme.
            primary = Color(0xFFE0B463),
            onPrimary = Color(0xFF241A05),
            secondary = Color(0xFF3D6B52),
            background = table.felt,
            surface = table.feltShadow,
            onBackground = Color(0xFFFDFBF4),
            onSurface = Color(0xFFFDFBF4),
        )
    }
    CompositionLocalProvider(LocalTableColors provides table) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
