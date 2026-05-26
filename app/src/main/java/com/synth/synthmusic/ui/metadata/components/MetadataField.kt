package com.synth.synthmusic.ui.metadata.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable labeled text field for metadata editing.
 *
 * @param label the label displayed above the field.
 * @param value the current text value.
 * @param onValueChange callback invoked when the text changes.
 * @param modifier the modifier to be applied to the field.
 * @param enabled whether the field is editable.
 */
@Composable
fun MetadataField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}
