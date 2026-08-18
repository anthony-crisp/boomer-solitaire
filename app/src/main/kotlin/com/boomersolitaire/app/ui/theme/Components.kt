package com.boomersolitaire.app.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
) {
    val lightTable = rememberLightTable()
    val interaction = remember { MutableInteractionSource() }
    val pressed: State<Boolean> = interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .glass(shape, GlassTier.RAISED, lightTable, pressed = { pressed.value })
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}
