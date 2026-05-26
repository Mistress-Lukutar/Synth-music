package com.synth.synthmusic.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.domain.model.Song

/**
 * Bottom sheet for sharing song audio or metadata.
 *
 * @param song Song to share.
 * @param onDismiss Dismiss callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSongSheet(
    song: Song?,
    onDismiss: () -> Unit
) {
    if (song == null) return
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Share",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(song.uri))
                    }
                    context.startActivity(Intent.createChooser(intent, "Share audio"))
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("Share Audio File", modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = {
                    val text = "${song.title} - ${song.artist}\n${song.album}"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share song info"))
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("Share Song Info", modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Song Info", "${song.title} - ${song.artist}")
                    clipboard.setPrimaryClip(clip)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Text("Copy to Clipboard", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
