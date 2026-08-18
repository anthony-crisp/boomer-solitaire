package com.boomersolitaire.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boomersolitaire.app.game.GameViewModel
import com.boomersolitaire.app.ui.theme.GlassButton
import com.boomersolitaire.app.ui.theme.LocalTableColors
import com.boomersolitaire.app.ui.theme.feltBackground

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

            Button(
                onClick = onPlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    if (resumable) "Continue game" else "Play",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
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
