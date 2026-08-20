package com.boomersolitaire.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boomersolitaire.app.game.GameSound
import com.boomersolitaire.app.game.GameViewModel
import com.boomersolitaire.app.game.HintMessage
import com.boomersolitaire.app.game.SoundManager
import com.boomersolitaire.app.ui.board.BarIcon
import com.boomersolitaire.app.ui.board.Board
import com.boomersolitaire.app.ui.board.BoardCallbacks
import com.boomersolitaire.app.ui.board.WinCelebration
import com.boomersolitaire.app.ui.board.drawBarIcon
import com.boomersolitaire.app.ui.theme.BackToMenuButton
import com.boomersolitaire.app.ui.theme.GlassPanel
import com.boomersolitaire.app.ui.theme.GlassTier
import com.boomersolitaire.app.ui.theme.glass
import com.boomersolitaire.app.ui.theme.rememberLightTable
import com.boomersolitaire.app.ui.theme.LocalTableColors
import com.boomersolitaire.app.ui.theme.feltBackground
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    vm: GameViewModel,
    onBackToMenu: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val table = LocalTableColors.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    val soundManager = remember { SoundManager(context.applicationContext) }
    LaunchedEffect(Unit) { soundManager.load() }
    DisposableEffect(Unit) { onDispose { soundManager.release() } }
    LaunchedEffect(vm) {
        vm.sounds.collect { sound ->
            if (vm.ui.value.settings.soundEnabled) soundManager.play(sound)
            when (sound) {
                GameSound.PLACE -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                GameSound.WIN -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                else -> Unit
            }
        }
    }
    LaunchedEffect(ui.shake?.nonce) {
        if (ui.shake != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(Unit) { vm.resumeOrNew() }
    LifecycleResumeEffect(Unit) {
        vm.onScreenResumed()
        onPauseOrDispose { vm.onScreenPaused() }
    }
    LaunchedEffect(ui.isDealing) {
        if (ui.isDealing) {
            delay(if (ui.settings.reduceMotion) 100 else 1500)
            vm.onDealAnimationDone()
        }
    }

    // Honour the system-wide "remove animations" accessibility setting too.
    val systemReduceMotion = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val effectiveSettings = if (systemReduceMotion) ui.settings.copy(reduceMotion = true) else ui.settings
    val onPrimarySparkle = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .feltBackground(table),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top bar: menu, timer (optional), finish-game offer.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackToMenuButton(onClick = onBackToMenu)
                Spacer(Modifier.weight(1f))
                if (ui.settings.showTimer && ui.state != null) {
                    Text(
                        formatDuration(ui.elapsedMs),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                if (ui.canAutoComplete && !ui.isAutoCompleting && ui.winSummary == null) {
                    Button(onClick = { vm.autoComplete() }) {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            drawBarIcon(BarIcon.HINT, onPrimarySparkle)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Finish game", fontSize = 17.sp)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Keep the stock pile clear of the Menu button's touch
                    // target — a mis-tap here exits the game.
                    .padding(top = 12.dp)
                    .then(
                        if (ui.isDealing) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures { vm.onDealAnimationDone() }
                            }
                        } else Modifier
                    ),
            ) {
                val state = ui.state
                if (state == null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.heightIn(min = 12.dp))
                        Text(
                            "Shuffling a winnable deal…",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp,
                        )
                    }
                } else {
                    val callbacks = remember(vm) {
                        BoardCallbacks(
                            onTapStock = vm::onTapStock,
                            onTapWaste = vm::onTapWaste,
                            onTapTableau = vm::onTapTableau,
                            onTapFoundation = vm::onTapFoundation,
                            onRequestMove = vm::requestMove,
                        )
                    }
                    Board(
                        state = state,
                        settings = effectiveSettings,
                        // Treat the cascade like the deal: the board is
                        // playing itself, so taps must not interleave.
                        isDealing = ui.isDealing || ui.isAutoCompleting,
                        hint = ui.hint,
                        shake = ui.shake,
                        callbacks = callbacks,
                    )
                }

                // Kind hint message.
                val message = ui.hint?.message
                if (message != null && message != HintMessage.NONE) {
                    GlassPanel(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .widthIn(max = 420.dp),
                        tier = GlassTier.RAISED,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = when (message) {
                                HintMessage.TRY_DRAWING -> "No moves on the table just now — try drawing from the deck."
                                else -> "No moves available right now. You can undo a few steps or start a fresh game."
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }

                // Win overlay with a gentle cascade behind it.
                val win = ui.winSummary
                if (win != null) {
                    WinCelebration(settings = effectiveSettings)
                    WinOverlay(
                        win = win,
                        onPlayAgain = { vm.newGame() },
                        onBackToMenu = onBackToMenu,
                    )
                }
            }

            // Fixed bottom bar: big labelled buttons.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val order = listOf("undo", "hint", "draw").let {
                    if (ui.settings.leftHanded) it.reversed() else it
                }
                for (slot in order) {
                    when (slot) {
                        "undo" -> BarButton(
                            label = "Undo",
                            icon = BarIcon.UNDO,
                            enabled = ui.canUndo && ui.winSummary == null,
                            modifier = Modifier.weight(1f),
                        ) { vm.undo() }
                        "hint" -> BarButton(
                            label = "Hint",
                            icon = BarIcon.HINT,
                            enabled = ui.state != null && ui.winSummary == null,
                            modifier = Modifier.weight(1f),
                        ) { vm.hint() }
                        "draw" -> BarButton(
                            label = "Draw",
                            icon = BarIcon.DRAW,
                            enabled = ui.state != null && ui.winSummary == null,
                            modifier = Modifier.weight(1f),
                        ) { vm.onTapStock() }
                    }
                }
            }
        }
    }
}

/**
 * A bar action: glass like the rest of the furniture, a drawn icon in the
 * cards' own language, and a press expressed as a material change (the
 * default ripple reads as flicker on glass).
 */
@Composable
private fun BarButton(
    label: String,
    icon: BarIcon,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val lightTable = rememberLightTable()
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(16.dp)
    val contentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.35f)
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .glass(shape, GlassTier.RAISED, lightTable, pressed = { pressed.value && enabled })
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClickLabel = label,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            drawBarIcon(icon, contentColor)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
    }
}

@Composable
private fun WinOverlay(
    win: com.boomersolitaire.app.game.WinSummary,
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        GlassPanel(
            shape = RoundedCornerShape(20.dp),
            tier = GlassTier.TRANSIENT,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("You won! 🎉", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.heightIn(min = 10.dp))
                Text(
                    "Time: ${formatDuration(win.durationMs)}   Moves: ${win.moves}",
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val records = buildList {
                    if (win.isFastestWin) add("Your fastest game yet!")
                    if (win.isFewestMoves) add("Fewest moves ever!")
                    if (win.isBestStreak) add("Best winning streak: ${win.streak}!")
                }
                for (r in records) {
                    Text(r, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                if (win.streak > 1 && !win.isBestStreak) {
                    Text("That's ${win.streak} wins in a row.", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.heightIn(min = 18.dp))
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Play again", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onBackToMenu,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Back to menu", fontSize = 18.sp)
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
