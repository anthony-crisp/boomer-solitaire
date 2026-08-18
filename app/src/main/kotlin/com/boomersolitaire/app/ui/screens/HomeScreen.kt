package com.boomersolitaire.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boomersolitaire.app.game.GameViewModel
import com.boomersolitaire.app.ui.board.CardArt
import com.boomersolitaire.app.ui.theme.GlassButton
import com.boomersolitaire.app.ui.theme.LocalTableColors
import com.boomersolitaire.app.ui.theme.feltBackground
import com.boomersolitaire.engine.Suit

@Composable
fun HomeScreen(
    vm: GameViewModel,
    hasSavedGame: Boolean,
    dayStreak: Long,
    onPlay: () -> Unit,
    onNewGame: () -> Unit,
    onScores: () -> Unit,
    onSettings: () -> Unit,
) {
    val table = LocalTableColors.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val resumable = hasSavedGame || (ui.state != null && !ui.isWon && ui.moveCount >= 0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .feltBackground(table),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark()
            Spacer(Modifier.height(20.dp))
            Text(
                "Boomer",
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                "Solitaire",
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            if (dayStreak > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "You've played $dayStreak days in a row ☀️",
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(36.dp))

            GlassButton(
                text = if (resumable) "Continue game" else "Play",
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth(),
                prominent = true,
            )
            Spacer(Modifier.height(14.dp))
            GlassButton(
                text = "New game",
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            GlassButton(
                text = "Scores",
                onClick = onScores,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            GlassButton(
                text = "Settings",
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The badge from the launcher icon: a felt disc of concentric hearts.
 * Fixed brand colours — a logo does not follow the theme.
 */
@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    val ring = Color(0xFF2A5C43)
    val disc = Color(0xFF5F8A6B)
    val cream = Color(0xFFF5EFDF)
    Canvas(modifier = modifier.size(112.dp)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(ring, r, c)
        drawCircle(disc, r * 0.95f, c)
        val hearts = listOf(0.72f to cream, 0.56f to disc, 0.42f to cream, 0.28f to disc, 0.155f to cream)
        hearts.forEachIndexed { i, (f, color) ->
            val s = 2f * r * f
            with(CardArt) {
                drawSuit(
                    Suit.HEARTS,
                    Offset(c.x - s / 2f, c.y - s / 2f + r * 0.06f - i * r * 0.012f),
                    s,
                    color,
                )
            }
        }
        drawCircle(cream, r * 0.1f, Offset(c.x, c.y - r * 0.7f))
        drawCircle(cream, r * 0.055f, Offset(c.x - r * 0.57f, c.y - r * 0.52f))
        drawCircle(cream, r * 0.055f, Offset(c.x + r * 0.57f, c.y - r * 0.52f))
    }
}
