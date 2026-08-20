package com.boomersolitaire.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boomersolitaire.app.data.GameRecord
import com.boomersolitaire.app.data.GameRecordDao
import com.boomersolitaire.app.data.computeModeStats
import com.boomersolitaire.app.ui.theme.BackToMenuButton
import com.boomersolitaire.app.ui.theme.GlassPanel
import com.boomersolitaire.app.ui.theme.LocalTableColors
import com.boomersolitaire.app.ui.theme.feltBackground
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(dao: GameRecordDao, onBack: () -> Unit) {
    val table = LocalTableColors.current
    val records by dao.all().collectAsStateWithLifecycle(initialValue = emptyList())
    val bestGames by dao.bestByTime(10).collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .feltBackground(table)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackToMenuButton(onClick = onBack)
            Spacer(Modifier.weight(1f))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Your games",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))

            for (drawThree in listOf(false, true)) {
                val stats = remember(records, drawThree) { computeModeStats(records, drawThree) }
                if (stats.started == 0 && drawThree) continue
                StatsCard(
                    title = if (drawThree) "Draw 3" else "Draw 1",
                    highlight = "Games won" to "${stats.won}",
                    rows = buildList {
                        add("Games started" to "${stats.started}")
                        if (stats.setAside > 0) add("Set aside for later" to "${stats.setAside}")
                        add("Winning streak" to "${stats.currentStreak}")
                        add("Best streak" to "${stats.bestStreak}")
                        stats.fastestWinMs?.let { add("Fastest win" to formatDuration(it)) }
                        stats.fewestMoves?.let { add("Fewest moves" to "$it") }
                    },
                )
                Spacer(Modifier.height(16.dp))
            }

            if (bestGames.isNotEmpty()) {
                Text(
                    "Best games",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(10.dp))
                BestGamesCard(bestGames)
                Spacer(Modifier.height(16.dp))
            }

            if (records.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Finish a game and your scores will appear here.",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    highlight: Pair<String, String>,
    rows: List<Pair<String, String>>,
) {
    GlassPanel(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            // Wins are the headline; everything else is quieter context.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    highlight.first,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    highlight.second,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            for ((label, value) in rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun BestGamesCard(games: List<GameRecord>) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }
    GlassPanel(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Date", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1.4f))
                Text("Time", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.8f))
                Text("Moves", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.8f))
            }
            Spacer(Modifier.height(6.dp))
            for (g in games) {
                val date = Instant.ofEpochMilli(g.endedAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                ) {
                    Text(dateFormat.format(date), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1.4f))
                    Text(formatDuration(g.durationMs), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.8f))
                    Text("${g.moves}", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.8f))
                }
            }
        }
    }
}
