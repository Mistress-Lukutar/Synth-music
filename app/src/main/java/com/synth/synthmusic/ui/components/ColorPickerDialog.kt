package com.synth.synthmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Centered modal color picker that matches the shared [MenuDialog] style.
 *
 * @param title Dialog title displayed at the top.
 * @param selected Currently selected color.
 * @param colors List of selectable colors. Defaults to [ArtworkPresetColors].
 * @param onSelected Called when the user chooses a color. The dialog is not
 *   dismissed automatically; callers can dismiss inside this callback.
 * @param onDismiss Called when the dialog is dismissed.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    selected: Color,
    colors: List<Color> = ArtworkPresetColors,
    onSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    MenuDialog(
        title = title,
        onDismiss = onDismiss
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 12.dp)
        ) {
            items(colors, key = { it.value.toString() }) { color ->
                val isSelected = color == selected
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Black.copy(alpha = 0.2f)
                            },
                            shape = CircleShape
                        )
                        .clickable { onSelected(color) }
                )
            }
        }
    }
}
