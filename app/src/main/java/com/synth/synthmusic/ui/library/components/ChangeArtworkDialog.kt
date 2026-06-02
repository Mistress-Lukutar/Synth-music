package com.synth.synthmusic.ui.library.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Simple dialog offering artwork change actions.
 *
 * @param onDismiss called when the dialog is dismissed.
 * @param onPick called when the user chooses to pick from gallery.
 * @param onRemove called when the user chooses to remove the artwork.
 */
@Composable
fun ChangeArtworkDialog(
    onDismiss: () -> Unit,
    onPick: () -> Unit,
    onRemove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change artwork") },
        text = { Text("Choose an action for this artwork.") },
        confirmButton = {
            TextButton(
                onClick = {
                    onPick()
                    onDismiss()
                }
            ) {
                Text("Pick from gallery")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onRemove()
                    onDismiss()
                }
            ) {
                Text("Remove")
            }
        }
    )
}
