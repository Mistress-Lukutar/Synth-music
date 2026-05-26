package com.synth.synthmusic.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.domain.model.AccentColor

/**
 * Accent color selection component.
 *
 * Presents the available accent colors as a radio group.
 *
 * @param selected the currently selected accent color.
 * @param onSelected callback invoked when a color is selected.
 * @param modifier the modifier to be applied to the picker.
 */
@Composable
fun AccentColorPicker(
    selected: AccentColor,
    onSelected: (AccentColor) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Accent Color",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        SettingDropdown(
            title = "",
            options = AccentColor.entries,
            selected = selected,
            onSelected = onSelected,
            label = { it.name }
        )
    }
}
