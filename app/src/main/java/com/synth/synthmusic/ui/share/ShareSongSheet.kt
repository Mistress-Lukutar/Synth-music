package com.synth.synthmusic.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.ui.components.MenuDialog
import com.synth.synthmusic.ui.components.MenuOptionRow

/**
 * Centered dialog for sharing song audio, metadata, lyrics, or copying info.
 *
 * @param song Song to share. When null the dialog is not shown.
 * @param onDismiss Dismiss callback.
 */
@Composable
fun ShareSongSheet(
    song: Song?,
    onDismiss: () -> Unit
) {
    if (song == null) return
    val context = LocalContext.current

    MenuDialog(
        title = "Share Track",
        onDismiss = onDismiss,
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val imageRequest = remember(song.artworkUri) {
                    ImageRequest.Builder(context)
                        .data(song.artworkUri)
                        .placeholder(R.drawable.ic_placeholder_artwork)
                        .error(R.drawable.ic_placeholder_artwork)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Album art",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        MenuOptionRow(
            icon = Icons.Outlined.Share,
            label = "Share Audio File",
            onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, song.uri.toUri())
                }
                context.startActivity(Intent.createChooser(intent, "Share audio"))
                onDismiss()
            }
        )
        MenuOptionRow(
            icon = Icons.Outlined.Info,
            label = "Share Song Info",
            onClick = {
                val text = "${song.title} - ${song.artist}\n${song.album}"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(intent, "Share song info"))
                onDismiss()
            }
        )
        if (!song.lyrics.isNullOrBlank()) {
            MenuOptionRow(
                icon = Icons.AutoMirrored.Outlined.Article,
                label = "Share Lyrics",
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, song.lyrics)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share lyrics"))
                    onDismiss()
                }
            )
        }
        MenuOptionRow(
            icon = Icons.Outlined.ContentCopy,
            label = "Copy to Clipboard",
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Song Info", "${song.title} - ${song.artist}")
                clipboard.setPrimaryClip(clip)
                onDismiss()
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
