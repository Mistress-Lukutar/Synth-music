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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Curated preset colors for generated artwork.
 */
val ArtworkPresetColors = listOf(
    Color(0xFFE53935),
    Color(0xFFD81B60),
    Color(0xFF8E24AA),
    Color(0xFF5E35B1),
    Color(0xFF3949AB),
    Color(0xFF1E88E5),
    Color(0xFF00ACC1),
    Color(0xFF00897B),
    Color(0xFF43A047),
    Color(0xFF7CB342),
    Color(0xFFFDD835),
    Color(0xFFFFB300),
    Color(0xFFFB8C00),
    Color(0xFFF4511E),
    Color(0xFF6D4C41),
    Color(0xFF546E7A),
    Color(0xFFFFFFFF),
    Color(0xFF000000)
)

/**
 * Grid of preset colors.
 *
 * @param selected Currently selected color.
 * @param onSelected Callback invoked when a color is selected.
 * @param modifier Modifier for layout. Height should be provided by the caller.
 */
@Composable
fun ColorPresetGrid(
    selected: Color,
    onSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 12.dp)
    ) {
        items(ArtworkPresetColors, key = { it.value.toString() }) { color ->
            val isSelected = color == selected
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
                    .clickable { onSelected(color) }
            )
        }
    }
}
