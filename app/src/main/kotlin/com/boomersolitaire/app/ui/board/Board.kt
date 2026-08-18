package com.boomersolitaire.app.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.boomersolitaire.app.data.Settings
import com.boomersolitaire.app.game.HintDestination
import com.boomersolitaire.app.game.HintHighlight
import com.boomersolitaire.app.game.ShakeEvent
import com.boomersolitaire.app.ui.theme.LocalTableColors
import com.boomersolitaire.engine.Card
import com.boomersolitaire.engine.GameState
import com.boomersolitaire.engine.Suit
import kotlin.math.roundToInt

class BoardCallbacks(
    val onTapStock: () -> Unit,
    val onTapWaste: () -> Unit,
    val onTapTableau: (column: Int, cardIndex: Int) -> Unit,
    val onTapFoundation: (Suit) -> Unit,
)

@Composable
fun Board(
    state: GameState,
    settings: Settings,
    isDealing: Boolean,
    hint: HintHighlight?,
    shake: ShakeEvent?,
    callbacks: BoardCallbacks,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val metrics = remember(widthPx, heightPx, settings.cardSize, settings.leftHanded) {
            computeBoardMetrics(widthPx, heightPx, density.density, settings.cardSize, settings.leftHanded)
        }
        val placements = remember(state, metrics) { computePlacements(state, metrics) }

        PileOutlines(metrics, state, callbacks, hint)

        // One composable per card, keyed by stable id; positions animate.
        for (card in Card.fullDeck) {
            val placement = placements[card.id] ?: continue
            androidx.compose.runtime.key(card.id) {
                BoardCard(
                    card = card,
                    placement = placement,
                    metrics = metrics,
                    settings = settings,
                    isDealing = isDealing,
                    hinted = hint?.cardIds?.contains(card.id) == true,
                    shake = shake?.takeIf { it.cardIds.contains(card.id) },
                    callbacks = callbacks,
                )
            }
        }
    }
}

@Composable
private fun PileOutlines(
    m: BoardMetrics,
    state: GameState,
    callbacks: BoardCallbacks,
    hint: HintHighlight?,
) {
    val table = LocalTableColors.current
    val density = LocalDensity.current
    val cardWDp: Dp
    val cardHDp: Dp
    with(density) {
        cardWDp = m.cardW.toDp()
        cardHDp = m.cardH.toDp()
    }
    val shape = RoundedCornerShape(with(density) { (m.cardW * 0.09f).toDp() })

    @Composable
    fun outline(pos: Offset, label: String, onClick: (() -> Unit)?, highlighted: Boolean, content: @Composable () -> Unit = {}) {
        val pulse = pulseAlpha(highlighted)
        Box(
            modifier = Modifier
                .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .size(cardWDp, cardHDp)
                .border(if (highlighted) 3.dp else 1.5.dp, if (highlighted) table.highlight.copy(alpha = pulse) else table.pileOutline, shape)
                .then(
                    if (onClick != null) {
                        Modifier
                            .clickable(onClickLabel = label) { onClick() }
                            .semantics { contentDescription = label }
                    } else Modifier.semantics { contentDescription = label }
                ),
            contentAlignment = Alignment.Center,
        ) { content() }
    }

    val stockHighlighted = hint?.destination == HintDestination.Stock
    outline(
        m.stockPos,
        label = if (state.stock.isEmpty() && state.waste.isNotEmpty()) "Turn the waste pile over" else "Stock pile. Tap to draw.",
        onClick = callbacks.onTapStock,
        highlighted = stockHighlighted,
    ) {
        if (state.stock.isEmpty()) {
            Text("↻", color = table.pileOutline, fontSize = with(density) { (m.cardW * 0.5f).toSp() })
        }
    }

    outline(m.wastePos, "Waste pile", onClick = null, highlighted = false)

    for (suit in Suit.entries) {
        val highlighted = (hint?.destination as? HintDestination.Foundation)?.suit == suit
        outline(
            m.foundationPos[suit.ordinal],
            label = foundationDescription(state, suit),
            onClick = null,
            highlighted = highlighted,
        ) {
            Text(
                suitGlyph(suit),
                color = table.pileOutline,
                fontSize = with(density) { (m.cardW * 0.5f).toSp() },
            )
        }
    }

    for (col in 0..6) {
        val highlighted = (hint?.destination as? HintDestination.TableauColumn)?.column == col
        if (state.tableau[col].cards.isEmpty() || highlighted) {
            val pile = state.tableau[col]
            val pos = if (pile.cards.isEmpty()) {
                Offset(m.tableauX[col], m.tableauTopY)
            } else {
                // Highlight lands where the moved card would go: just under the pile top.
                Offset(m.tableauX[col], m.tableauTopY)
            }
            outline(
                pos,
                label = "Column ${col + 1}, empty",
                onClick = null,
                highlighted = highlighted,
            )
        }
    }
}

private fun foundationDescription(state: GameState, suit: Suit): String {
    val top = state.foundationTop(suit)
    return if (top == null) "Foundation for ${suitName(suit)}, empty"
    else "Foundation for ${suitName(suit)}, showing ${cardName(top)}"
}

@Composable
private fun pulseAlpha(active: Boolean): Float {
    if (!active) return 1f
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    return alpha
}

@Composable
private fun BoardCard(
    card: Card,
    placement: CardPlacement,
    metrics: BoardMetrics,
    settings: Settings,
    isDealing: Boolean,
    hinted: Boolean,
    shake: ShakeEvent?,
    callbacks: BoardCallbacks,
) {
    val density = LocalDensity.current
    val table = LocalTableColors.current
    val target = Offset(placement.x, placement.y)
    val position = remember {
        Animatable(if (isDealing) metrics.stockPos else target, Offset.VectorConverter)
    }
    LaunchedEffect(target, settings.reduceMotion) {
        if (settings.reduceMotion) {
            position.snapTo(target)
        } else {
            position.animateTo(
                target,
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Offset(0.5f, 0.5f)),
            )
        }
    }

    // Gentle shake for an invalid move; the card returns home.
    val shakeX = remember { Animatable(0f) }
    LaunchedEffect(shake?.nonce) {
        if (shake != null) {
            val amp = metrics.cardW * 0.06f
            shakeX.animateTo(
                0f,
                keyframes {
                    durationMillis = 320
                    amp at 40
                    -amp at 120
                    amp * 0.5f at 200
                    0f at 320
                },
            )
        }
    }

    val moving = position.isRunning
    val pulse = pulseAlpha(hinted)
    val shape = RoundedCornerShape(with(density) { (metrics.cardW * 0.09f).toDp() })
    val cardWDp: Dp
    val cardHDp: Dp
    with(density) {
        cardWDp = metrics.cardW.toDp()
        cardHDp = metrics.cardH.toDp()
    }

    val tapLabel = describeCard(card, placement)
    val clickTarget = placement.tap
    Box(
        modifier = Modifier
            .offset { IntOffset((position.value.x + shakeX.value).roundToInt(), position.value.y.roundToInt()) }
            .zIndex(placement.z + if (moving || shakeX.isRunning) 200f else 0f)
            .size(cardWDp, cardHDp)
            .graphicsLayer {
                if (moving) {
                    shadowElevation = 12.dp.toPx()
                    this.shape = shape
                }
            }
            .then(
                if (hinted) Modifier.border(3.dp, table.highlight.copy(alpha = pulse), shape) else Modifier
            )
            .then(
                if (clickTarget != null) {
                    Modifier.clickable(onClickLabel = tapLabel) {
                        when (clickTarget) {
                            is TapTarget.Stock -> callbacks.onTapStock()
                            is TapTarget.Waste -> callbacks.onTapWaste()
                            is TapTarget.Tableau -> callbacks.onTapTableau(clickTarget.column, clickTarget.cardIndex)
                            is TapTarget.Foundation -> callbacks.onTapFoundation(clickTarget.suit)
                        }
                    }
                } else Modifier
            )
            .semantics { contentDescription = tapLabel },
    ) {
        if (placement.faceUp) {
            CardFace(card, metrics, settings, shape)
        } else {
            CardBack(metrics, shape)
        }
    }
}

@Composable
private fun CardFace(card: Card, metrics: BoardMetrics, settings: Settings, shape: RoundedCornerShape) {
    val table = LocalTableColors.current
    val density = LocalDensity.current
    val suitColor = when {
        !settings.fourColorDeck -> if (card.isRed) table.red else table.black
        else -> when (card.suit) {
            Suit.SPADES -> table.black
            Suit.HEARTS -> table.red
            Suit.DIAMONDS -> table.blue
            Suit.CLUBS -> table.green
        }
    }
    val indexSize = with(density) { (metrics.cardW * 0.30f * metrics.indexScale).toSp() }
    val centerSize = with(density) { (metrics.cardW * 0.44f).toSp() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(table.cardFace, shape)
            .border(1.dp, table.cardEdge, shape),
    ) {
        Text(
            text = "${rankLabel(card.rank)}${suitGlyph(card.suit)}",
            color = suitColor,
            fontSize = indexSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 3.dp, y = 0.dp),
        )
        Text(
            text = suitGlyph(card.suit),
            color = suitColor,
            fontSize = centerSize,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-3).dp, y = 0.dp),
        )
    }
}

@Composable
private fun CardBack(metrics: BoardMetrics, shape: RoundedCornerShape) {
    val table = LocalTableColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(table.cardBack, shape)
            .border(1.dp, table.cardBackAccent.copy(alpha = 0.6f), shape),
    )
}
