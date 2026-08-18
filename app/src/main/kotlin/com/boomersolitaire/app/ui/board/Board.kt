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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.boomersolitaire.app.data.Settings
import com.boomersolitaire.app.game.HintDestination
import com.boomersolitaire.app.game.HintHighlight
import com.boomersolitaire.app.game.ShakeEvent
import com.boomersolitaire.app.ui.theme.GlassTier
import com.boomersolitaire.app.ui.theme.LocalTableColors
import com.boomersolitaire.app.ui.theme.glass
import com.boomersolitaire.app.ui.theme.grainBrush
import com.boomersolitaire.app.ui.theme.rememberLightTable
import com.boomersolitaire.engine.Card
import com.boomersolitaire.engine.GameState
import com.boomersolitaire.engine.Move
import com.boomersolitaire.engine.Suit
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class BoardCallbacks(
    val onTapStock: () -> Unit,
    val onTapWaste: () -> Unit,
    val onTapTableau: (column: Int, cardIndex: Int) -> Unit,
    val onTapFoundation: (Suit) -> Unit,
    /** Drag-and-drop: attempt [move]; shake [cardIds] if it is illegal. */
    val onRequestMove: (move: Move, cardIds: List<Int>) -> Unit,
)

/** A drag in progress: the run of cards being carried and its finger offset. */
private class DragInfo(
    val source: TapTarget,
    val cardIds: List<Int>,
    val offset: Animatable<Offset, *>,
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
        val scope = rememberCoroutineScope()
        val dragState = remember { mutableStateOf<DragInfo?>(null) }

        fun startDrag(source: TapTarget) {
            val ids = when (source) {
                is TapTarget.Waste -> listOfNotNull(state.wasteTop?.id)
                is TapTarget.Tableau -> {
                    val col = state.tableau[source.column]
                    if (source.cardIndex in col.faceDownCount until col.cards.size) {
                        col.cards.subList(source.cardIndex, col.cards.size).map(Card::id)
                    } else emptyList()
                }
                else -> emptyList()
            }
            if (ids.isNotEmpty()) {
                dragState.value = DragInfo(source, ids, Animatable(Offset.Zero, Offset.VectorConverter))
            }
        }

        fun endDrag() {
            val drag = dragState.value ?: return
            val headId = drag.cardIds.first()
            val origin = placements[headId] ?: return
            val dropPos = Offset(origin.x, origin.y) + drag.offset.value
            val move = resolveDrop(state, metrics, drag.source, dropPos)
            if (move != null) callbacks.onRequestMove(move, drag.cardIds)
            scope.launch {
                drag.offset.animateTo(
                    Offset.Zero,
                    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium, visibilityThreshold = Offset(0.5f, 0.5f)),
                )
                if (dragState.value === drag) dragState.value = null
            }
        }

        PileOutlines(metrics, state, callbacks, hint)

        // The hint's destination pulse must sit on top of a non-empty pile,
        // so it is attached to that pile's top card.
        val hintDestCardId = when (val dest = hint?.destination) {
            is HintDestination.TableauColumn -> state.tableau[dest.column].topCard?.id
            is HintDestination.Foundation -> state.foundationTop(dest.suit)?.id
            else -> null
        }

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
                    hinted = hint?.cardIds?.contains(card.id) == true || card.id == hintDestCardId,
                    shake = shake?.takeIf { it.cardIds.contains(card.id) },
                    callbacks = callbacks,
                    dragState = dragState,
                    onDragStart = ::startDrag,
                    onDrag = { delta ->
                        dragState.value?.let { scope.launch { it.offset.snapTo(it.offset.value + delta) } }
                    },
                    onDragEnd = ::endDrag,
                )
            }
        }
    }
}

/**
 * Turn a drop position into a move, with generous targets: anywhere near a
 * foundation slot sends the card to its own foundation; otherwise the nearest
 * tableau column within most of a card-width catches the drop.
 */
private fun resolveDrop(
    state: GameState,
    m: BoardMetrics,
    source: TapTarget,
    dropPos: Offset,
): Move? {
    val center = dropPos + Offset(m.cardW / 2f, m.cardH / 2f)
    val singleCard = when (source) {
        is TapTarget.Waste -> state.wasteTop
        is TapTarget.Tableau ->
            state.tableau[source.column].cards.let { if (source.cardIndex == it.size - 1) it[source.cardIndex] else null }
        else -> null
    }

    // Near any foundation slot → the card's own foundation.
    if (singleCard != null) {
        val nearFoundation = Suit.entries.any { suit ->
            val pos = m.foundationPos[suit.ordinal]
            center.x in (pos.x - m.cardW * 0.35f)..(pos.x + m.cardW * 1.35f) &&
                center.y in (pos.y - m.cardH * 0.4f)..(pos.y + m.cardH * 1.4f)
        }
        if (nearFoundation) {
            return when (source) {
                is TapTarget.Waste -> Move.WasteToFoundation(singleCard.suit)
                is TapTarget.Tableau -> Move.TableauToFoundation(source.column)
                else -> null
            }
        }
    }

    // Nearest tableau column by horizontal distance.
    val sourceColumn = (source as? TapTarget.Tableau)?.column
    val target = (0..6)
        .filter { it != sourceColumn }
        .minByOrNull { kotlin.math.abs(m.tableauX[it] + m.cardW / 2f - center.x) }
        ?.takeIf { kotlin.math.abs(m.tableauX[it] + m.cardW / 2f - center.x) < m.cardW * 0.9f }
        ?.takeIf { center.y > m.tableauTopY - m.cardH * 0.5f }
        ?: return null

    return when (source) {
        is TapTarget.Waste -> Move.WasteToTableau(target)
        is TapTarget.Tableau -> Move.TableauToTableau(source.column, source.cardIndex, target)
        else -> null
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
    val lightTable = rememberLightTable()

    @Composable
    fun outline(pos: Offset, label: String, onClick: (() -> Unit)?, highlighted: Boolean, content: @Composable () -> Unit = {}) {
        val pulse = pulseAlpha(highlighted)
        Box(
            modifier = Modifier
                .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .size(cardWDp, cardHDp)
                .glass(shape, GlassTier.VEIL, lightTable)
                .then(
                    if (highlighted) Modifier.border(3.dp, table.highlight.copy(alpha = pulse), shape) else Modifier
                )
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
            Canvas(modifier = Modifier.size(cardWDp * 0.5f)) {
                with(CardArt) {
                    drawSuit(suit, Offset.Zero, size.width, table.pileOutline)
                }
            }
        }
    }

    for (col in 0..6) {
        // Non-empty destination highlights ride on the pile's top card instead.
        if (state.tableau[col].cards.isEmpty()) {
            val highlighted = (hint?.destination as? HintDestination.TableauColumn)?.column == col
            outline(
                Offset(m.tableauX[col], m.tableauTopY),
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
    dragState: State<DragInfo?>,
    onDragStart: (TapTarget) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val table = LocalTableColors.current
    val target = Offset(placement.x, placement.y)
    val position = remember {
        Animatable(if (isDealing) metrics.stockPos else target, Offset.VectorConverter)
    }
    LaunchedEffect(target, settings.reduceMotion, isDealing) {
        if (settings.reduceMotion) {
            position.snapTo(target)
        } else {
            if (isDealing && placement.dealOrder != null && position.value != target) {
                kotlinx.coroutines.delay(placement.dealOrder * 26L)
            }
            position.animateTo(
                target,
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Offset(0.5f, 0.5f)),
            )
        }
    }

    // 3D flip: 0f shows the back, 180f the face.
    val flip = remember { Animatable(if (placement.faceUp) 180f else 0f) }
    LaunchedEffect(placement.faceUp, settings.reduceMotion, isDealing) {
        val end = if (placement.faceUp) 180f else 0f
        if (settings.reduceMotion) {
            flip.snapTo(end)
        } else {
            if (isDealing && placement.dealOrder != null && flip.value != end) {
                kotlinx.coroutines.delay(placement.dealOrder * 26L + 120L)
            }
            flip.animateTo(end, tween(durationMillis = 260))
        }
    }
    val showFace = flip.value > 90f

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
    val flipping = flip.isRunning
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
    val inDragRun = dragState.value?.cardIds?.contains(card.id) == true
    val draggable = clickTarget is TapTarget.Waste || clickTarget is TapTarget.Tableau
    Box(
        modifier = Modifier
            .offset {
                val drag = dragState.value?.takeIf { it.cardIds.contains(card.id) }?.offset?.value ?: Offset.Zero
                IntOffset(
                    (position.value.x + shakeX.value + drag.x).roundToInt(),
                    (position.value.y + drag.y).roundToInt(),
                )
            }
            .zIndex(placement.z + if (inDragRun) 300f else if (moving || flipping || shakeX.isRunning) 200f else 0f)
            .size(cardWDp, cardHDp)
            .then(
                if (draggable) {
                    Modifier.pointerInput(clickTarget) {
                        detectDragGestures(
                            onDragStart = { onDragStart(clickTarget!!) },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        )
                    }
                } else Modifier
            )
            .graphicsLayer {
                rotationY = flip.value - 180f
                cameraDistance = 16f * density.density
                if (moving || flipping || inDragRun) {
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
        if (showFace) {
            CardFace(card, metrics, settings, shape)
        } else {
            // The back is mirrored by the flip rotation; mirror it again so
            // its pattern reads correctly at rest.
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                CardBack(metrics, settings, shape)
            }
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
    // Oversized indices: big rank top-left, big suit top-right — both stay
    // visible in a fanned column, whichever hand holds the phone.
    val indexSize = with(density) { (metrics.cardW * 0.30f * metrics.indexScale).toSp() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(table.cardFace, shape)
            .border(1.dp, table.cardEdge, shape),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Plate thickness: a whisper of light at the top edge, a settled
            // shadow line at the bottom. Opaque underneath, always.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.35f),
                    0.18f to Color.Transparent,
                    0.9f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.05f),
                ),
            )
            with(CardArt) {
                // Centre art first; the corner index draws last so nothing
                // ever strokes over it.
                if (card.rank > 10) {
                    drawFaceCenter(card, w, h, suitColor, table.highlight.copy(alpha = 0.9f), metrics.indexScale)
                } else {
                    drawFaceCenter(card, w, h, suitColor, table.highlight, metrics.indexScale)
                }
                val s = w * 0.21f * metrics.indexScale
                drawSuit(card.suit, Offset(w - s - w * 0.06f, w * 0.07f), s, suitColor)
            }
        }
        
        Text(
            text = rankLabel(card.rank),
            color = suitColor,
            fontSize = indexSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            lineHeight = indexSize,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 4.dp, y = 1.dp),
        )
    }
}

@Composable
private fun CardBack(metrics: BoardMetrics, settings: Settings, shape: RoundedCornerShape) {
    val table = LocalTableColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(table.cardBack, shape)
            .border(1.dp, table.cardBackAccent.copy(alpha = 0.6f), shape),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            with(CardArt) {
                drawCardBack(settings.cardBack, size.width, size.height, table)
            }
            drawRect(grainBrush(), alpha = 0.04f)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.12f),
                    0.25f to Color.Transparent,
                ),
            )
        }
    }
}
