package com.boomersolitaire.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The back-to-menu affordance used on every screen: a drawn chevron (round
 * caps, like the bar icons) plus a plain-language label.
 */
@Composable
fun BackToMenuButton(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onBackground
    TextButton(onClick = onClick) {
        Canvas(modifier = Modifier.size(15.dp)) {
            val stroke = size.width * 0.18f
            val xTip = size.width * 0.28f
            val xTail = size.width * 0.72f
            drawLine(color, Offset(xTail, size.height * 0.14f), Offset(xTip, size.height * 0.5f), stroke, StrokeCap.Round)
            drawLine(color, Offset(xTip, size.height * 0.5f), Offset(xTail, size.height * 0.86f), stroke, StrokeCap.Round)
        }
        Spacer(Modifier.width(7.dp))
        Text("Menu", color = color, fontSize = 18.sp)
    }
}

/** A furniture surface: menus, dialogs, toasts, stat cards. Never a card face. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    tier: GlassTier = GlassTier.RAISED,
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable () -> Unit,
) {
    val lightTable = rememberLightTable()
    Box(modifier = modifier.glass(shape, tier, lightTable)) {
        content()
    }
}

/**
 * A secondary action on the table. The default ripple flickers on glass, so
 * the press is expressed as a material change instead — read in the draw
 * phase, never in composition.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    val lightTable = rememberLightTable()
    val interaction = remember { MutableInteractionSource() }
    val pressed: State<Boolean> = interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .heightIn(min = if (prominent) 72.dp else 56.dp)
            .glass(shape, GlassTier.RAISED, lightTable, pressed = { pressed.value })
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = if (prominent) 26.sp else 20.sp,
            fontWeight = if (prominent) FontWeight.Bold else null,
            color = if (prominent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )
    }
}
