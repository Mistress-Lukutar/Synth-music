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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.domain.model.GeneratedArtworkIcon

/**
 * Centered modal icon picker that matches the shared [MenuDialog] style.
 *
 * Shows the available [GeneratedArtworkIcon] entries as a grid of standard
 * Material icons.
 *
 * @param selected Currently selected icon.
 * @param onSelected Called when the user chooses an icon. The dialog is not
 *   dismissed automatically; callers can dismiss inside this callback.
 * @param onDismiss Called when the dialog is dismissed.
 */
@Composable
fun IconPickerDialog(
    selected: GeneratedArtworkIcon,
    onSelected: (GeneratedArtworkIcon) -> Unit,
    onDismiss: () -> Unit
) {
    MenuDialog(
        title = "Choose icon",
        onDismiss = onDismiss
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(horizontal = 12.dp)
        ) {
            items(GeneratedArtworkIcon.entries, key = { it.name }) { icon ->
                val isSelected = icon == selected
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelected(icon) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon.imageVector,
                        contentDescription = icon.name,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
