package com.synth.synthmusic.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * App-wide styled list row used across all screens.
 *
 * Wraps Material 3 [ListItem] with the app's list styling: the square container
 * surface M3 1.4 draws by default is removed (transparent container) and the row
 * is clipped to the app's 12 dp rounded shape so highlights and ripples stay rounded.
 *
 * @param headlineContent Primary content of the row.
 * @param modifier Modifier for styling.
 * @param overlineContent Optional content displayed above the headline.
 * @param supportingContent Optional secondary text below the headline.
 * @param leadingContent Optional content displayed at the start of the row.
 * @param trailingContent Optional content displayed at the end of the row.
 */
@Composable
fun SynthListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = headlineContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
    )
}
