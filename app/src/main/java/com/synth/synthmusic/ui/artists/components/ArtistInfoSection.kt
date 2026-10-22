package com.synth.synthmusic.ui.artists.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.ui.ai.components.MarkdownText

/**
 * Expandable artist-bio section displayed on the Artist detail screen.
 *
 * Three visual states:
 * - **Edit mode**: a [Card] wrapping a multiline [OutlinedTextField] for the bio draft,
 *   with Cancel and Save actions.
 * - **View mode, bio present**: a collapsed preview showing a circular artwork thumbnail
 *   alongside a truncated Markdown rendering, plus a "Show more" affordance; expanding
 *   reveals the full bio and a larger artwork image.
 * - **View mode, bio null**: a compact row with the artist's circular artwork and a
 *   clickable "Add artist info" label that enters edit mode.
 *
 * @param artist Artist whose bio is displayed.
 * @param isEditing Whether the section is in edit mode.
 * @param isExpanded Whether the bio is expanded (view mode only).
 * @param draft The current text draft shown in edit mode.
 * @param onDraftChange Callback invoked with the updated draft text.
 * @param onStartEdit Lambda called when the user wants to start editing.
 * @param onCancel Lambda called when the user cancels editing.
 * @param onSave Lambda called when the user saves the bio.
 * @param onToggleExpanded Lambda called to toggle expanded state; receives the new value.
 * @param modifier Modifier for styling.
 */
@Composable
fun ArtistInfoSection(
    artist: Artist,
    isEditing: Boolean,
    isExpanded: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onToggleExpanded: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isEditing) {
        EditModeCard(
            draft = draft,
            onDraftChange = onDraftChange,
            onCancel = onCancel,
            onSave = onSave,
            modifier = modifier
        )
    } else if (artist.bio != null) {
        ViewModeCard(
            artist = artist,
            isExpanded = isExpanded,
            onToggleExpanded = onToggleExpanded,
            onStartEdit = onStartEdit,
            modifier = modifier
        )
    } else {
        AddBioRow(
            artworkUri = artist.artworkUri,
            onStartEdit = onStartEdit,
            modifier = modifier
        )
    }
}

// ---------------------------------------------------------------------------
// Edit mode
// ---------------------------------------------------------------------------

/**
 * Card container for the bio-edit form.
 */
@Composable
private fun EditModeCard(
    draft: String,
    onDraftChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text(stringResource(R.string.artist_info_field_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 14,
                supportingText = {
                    Text(stringResource(R.string.artist_info_markdown_hint))
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
                TextButton(onClick = onSave) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// View mode — bio present
// ---------------------------------------------------------------------------

/**
 * Card wrapping the expanded or collapsed artist bio.
 */
@Composable
private fun ViewModeCard(
    artist: Artist,
    isExpanded: Boolean,
    onToggleExpanded: (Boolean) -> Unit,
    onStartEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        if (isExpanded) {
            ExpandedBioContent(
                artist = artist,
                onToggleExpanded = onToggleExpanded
            )
        } else {
            CollapsedBioContent(
                artist = artist,
                onToggleExpanded = onToggleExpanded,
                onStartEdit = onStartEdit
            )
        }
    }
}

/**
 * Expanded bio: large artwork + full MarkdownText + "Show less".
 */
@Composable
private fun ExpandedBioContent(
    artist: Artist,
    onToggleExpanded: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        CenteredArtistArtwork(
            artworkUri = artist.artworkUri,
            sizeDp = 96
        )
        Spacer(modifier = Modifier.height(12.dp))
        MarkdownText(markdown = artist.bio!!)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onToggleExpanded(false) }) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.artist_info_show_less))
            }
        }
    }
}

/**
 * Collapsed bio: artwork thumbnail + truncated MarkdownText preview + "Show more".
 */
@Composable
private fun CollapsedBioContent(
    artist: Artist,
    onToggleExpanded: (Boolean) -> Unit,
    onStartEdit: () -> Unit
) {
    val density = LocalDensity.current
    val bio = artist.bio!!

    // Measure the raw bio in bodyMedium style at the true available width.
    // This is a plain-text line count approximation — Markdown formatting does not affect line count here.
    val textMeasurer = rememberTextMeasurer()
    var overflow by remember(bio) { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        CenteredArtistArtwork(
            artworkUri = artist.artworkUri,
            sizeDp = 56
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Preview box: constrain height to ~4 body lines (~88 dp) with a fade gradient.
            BoxWithConstraints(
                modifier = Modifier.heightIn(max = 88.dp)
            ) {
                // maxWidth inside the weighted Column is already MarkdownText's actual width
                // (Row width minus card padding, artwork and spacing).
                val availableTextWidthPx = with(density) { maxWidth.toPx() }
                val lineCount = textMeasurer.measure(
                    text = AnnotatedString(bio),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 100,
                    constraints = Constraints(maxWidth = availableTextWidthPx.toInt())
                ).lineCount

                // Hoist overflow so the fade gradient and "Show more" use the same predicate.
                if (lineCount > 4) {
                    overflow = true
                } else {
                    overflow = false
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    MarkdownText(markdown = bio)
                    // Bottom fade gradient overlay.
                    if (lineCount > 4) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f),
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1.0f)
                                        )
                                    )
                                )
                        )
                    }
                }
            }

            // "Show more" affordance when text overflows the preview height.
            if (overflow) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.clickable(onClick = { onToggleExpanded(true) })
                ) {
                    Text(
                        text = stringResource(R.string.artist_info_show_more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // "Add artist info" shown at bottom when collapsed and no overflow (short bio).
            if (!overflow) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.artist_info_add),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onStartEdit)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// View mode — bio null
// ---------------------------------------------------------------------------

/**
 * Compact row with artwork and "Add artist info" label.
 */
@Composable
private fun AddBioRow(
    artworkUri: String?,
    onStartEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CenteredArtistArtwork(artworkUri = artworkUri, sizeDp = 56)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.artist_info_add),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onStartEdit)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared artwork helpers
// ---------------------------------------------------------------------------

/**
 * Circular artist artwork image at the given size.
 *
 * Uses [SubcomposeAsyncImage] so that when [artworkUri] is null, a placeholder
 * painter (the existing Person-in-surfaceVariant-circle look) renders identically
 * to the current [ArtistListItem] icon.
 */
@Composable
private fun CenteredArtistArtwork(
    artworkUri: String?,
    sizeDp: Int
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest = remember(artworkUri) {
        ImageRequest.Builder(context)
            .data(artworkUri)
            .placeholder(R.drawable.ic_placeholder_artwork)
            .error(R.drawable.ic_placeholder_artwork)
            .build()
    }

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = null,
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Crop,
        loading = {
            // Placeholder while loading.
            Box(
                modifier = Modifier
                    .size(sizeDp.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size((sizeDp * 0.5f).dp)
                )
            }
        },
        error = {
            // Fallback to Person icon on error.
            Box(
                modifier = Modifier
                    .size(sizeDp.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size((sizeDp * 0.5f).dp)
                )
            }
        }
    )
}
