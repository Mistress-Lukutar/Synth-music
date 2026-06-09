package com.synth.synthmusic.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A settings row with an optional icon, title, description and a switch.
 *
 * @param title the label text.
 * @param checked the current switch state.
 * @param onCheckedChange callback invoked when the switch is toggled.
 * @param description optional secondary text shown below the title.
 * @param icon optional leading icon.
 * @param modifier the modifier to be applied to the row.
 */
@Composable
fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = description?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = icon?.let {
            { Icon(imageVector = it, contentDescription = null) }
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = modifier
    )
}
