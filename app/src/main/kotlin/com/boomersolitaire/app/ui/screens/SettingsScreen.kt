package com.boomersolitaire.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boomersolitaire.app.data.AppTheme
import com.boomersolitaire.app.data.CardBack
import com.boomersolitaire.app.data.CardSize
import com.boomersolitaire.app.data.Settings
import com.boomersolitaire.app.data.SettingsRepository
import com.boomersolitaire.app.ui.theme.LocalTableColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repo: SettingsRepository, onBack: () -> Unit) {
    val table = LocalTableColors.current
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(table.felt)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("‹ Menu", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))

            SettingsCard("The game") {
                ToggleRow(
                    "Draw three cards",
                    "Turn three cards at a time from the deck. Off means one card — the easier, classic way.",
                    settings.drawThree,
                ) { scope.launch { repo.setDrawThree(it) } }
                ToggleRow(
                    "All deals winnable",
                    "Every new game can definitely be won. Turn off for completely random deals.",
                    settings.winnableDeals,
                ) { scope.launch { repo.setWinnableDeals(it) } }
                ToggleRow(
                    "Show timer",
                    "Show how long the game has taken, at the top of the screen. No pressure either way.",
                    settings.showTimer,
                ) { scope.launch { repo.setShowTimer(it) } }
            }

            SettingsCard("Looks") {
                ChoiceRow(
                    "Table style",
                    "How the table and cards look.",
                    options = listOf(
                        AppTheme.AUTO to "Auto",
                        AppTheme.FELT to "Green felt",
                        AppTheme.LINEN to "Linen",
                        AppTheme.DARK to "Dark",
                        AppTheme.HIGH_CONTRAST to "High contrast",
                    ),
                    selected = settings.theme,
                ) { scope.launch { repo.setTheme(it) } }
                ChoiceRow(
                    "Card size",
                    "Bigger cards are easier to read and tap.",
                    options = listOf(
                        CardSize.NORMAL to "Normal",
                        CardSize.LARGE to "Large",
                        CardSize.EXTRA_LARGE to "Extra large",
                    ),
                    selected = settings.cardSize,
                ) { scope.launch { repo.setCardSize(it) } }
                ChoiceRow(
                    "Card back",
                    "The pattern on the back of the cards.",
                    options = listOf(
                        CardBack.STRIPES to "Stripes",
                        CardBack.LATTICE to "Lattice",
                        CardBack.SUNBURST to "Sunburst",
                    ),
                    selected = settings.cardBack,
                ) { scope.launch { repo.setCardBack(it) } }
                ToggleRow(
                    "Four-colour deck",
                    "Each suit gets its own colour, so suits are easier to tell apart.",
                    settings.fourColorDeck,
                ) { scope.launch { repo.setFourColorDeck(it) } }
            }

            SettingsCard("Comfort") {
                ToggleRow(
                    "Left-handed layout",
                    "Mirrors the table so the deck sits on the right.",
                    settings.leftHanded,
                ) { scope.launch { repo.setLeftHanded(it) } }
                ToggleRow(
                    "Card sounds",
                    "Soft sounds when cards move. Never any music.",
                    settings.soundEnabled,
                ) { scope.launch { repo.setSoundEnabled(it) } }
                ToggleRow(
                    "Reduce motion",
                    "Calmer, quicker animations.",
                    settings.reduceMotion,
                ) { scope.launch { repo.setReduceMotion(it) } }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Boomer Solitaire never shows ads, never asks for money, and never connects to the internet.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .padding(bottom = 16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(description, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    description: String,
    options: List<Pair<T, String>>,
    selected: T,
    onChange: (T) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(description, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            for ((value, label) in options) {
                FilterChip(
                    selected = value == selected,
                    onClick = { onChange(value) },
                    label = { Text(label, fontSize = 16.sp) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
    }
}
