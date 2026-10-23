package com.synth.synthmusic.ui.metadata

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.ui.metadata.components.ArtworkPicker
import com.synth.synthmusic.ui.metadata.components.MetadataField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Song metadata detail screen supporting both read-only viewing and in-place editing.
 *
 * The screen displays song information across three tabs (Details, Lyrics, Artwork)
 * and toggles into an editable mode when the user taps the edit action. All edits
 * are committed together on save.
 *
 * @param songId ID of the song to display.
 * @param onNavigateBack Callback to navigate back.
 * @param modifier Modifier for styling.
 * @param viewModel The ViewModel for this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoScreen(
    songId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SongInfoViewModel = koinViewModel { parametersOf(songId) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val song = uiState.song
    val isEditing = uiState.isEditing
    val canEdit = uiState.hasWritePermission

    // Edit-field local state, re-initialized whenever edit mode is entered (or the song changes).
    var title by remember(isEditing, song?.id) { mutableStateOf(song?.title ?: "") }
    var artist by remember(isEditing, song?.id) { mutableStateOf(song?.artist ?: "") }
    var album by remember(isEditing, song?.id) { mutableStateOf(song?.album ?: "") }
    var genre by remember(isEditing, song?.id) { mutableStateOf(song?.genre ?: "") }
    var year by remember(isEditing, song?.id) { mutableStateOf(song?.year?.toString() ?: "") }
    var comment by remember(isEditing, song?.id) { mutableStateOf(song?.comment ?: "") }
    var lyrics by remember(isEditing, song?.id) { mutableStateOf(song?.lyrics ?: "") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Artwork picker launcher — reads selected image bytes and pushes to ViewModel.
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                withContext(Dispatchers.Main) {
                    viewModel.onEvent(SongInfoEvent.ArtworkPicked(bytes))
                }
            }
        }
    }

    // Re-check write permission when the screen resumes (e.g. after granting in settings).
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(SongInfoEvent.RefreshPermission)
        onPauseOrDispose { }
    }

    // System back while editing exits edit mode (discards unsaved edits).
    BackHandler(enabled = isEditing) {
        viewModel.onEvent(SongInfoEvent.ToggleEditMode)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Metadata" else "Song Info") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditing) {
                            viewModel.onEvent(SongInfoEvent.ToggleEditMode)
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!isEditing) {
                        IconButton(onClick = {
                            viewModel.onEvent(SongInfoEvent.ToggleEditMode)
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit metadata")
                        }
                    } else if (song != null && canEdit) {
                        TextButton(onClick = {
                            viewModel.onEvent(
                                SongInfoEvent.Save(
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    genre = genre,
                                    year = year,
                                    comment = comment,
                                    lyrics = lyrics
                                )
                            )
                        }) {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isEditing && !canEdit) {
                PermissionBanner()
            }

            val tabs = listOf("Details", "Lyrics", "Artwork")
            var selectedTab by remember { mutableIntStateOf(0) }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, tabTitle ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tabTitle) }
                    )
                }
            }

            when (selectedTab) {
                0 -> if (isEditing) {
                    EditableDetailsTab(
                        title = title,
                        onTitleChange = { title = it },
                        artist = artist,
                        onArtistChange = { artist = it },
                        album = album,
                        onAlbumChange = { album = it },
                        genre = genre,
                        onGenreChange = { genre = it },
                        year = year,
                        onYearChange = { year = it },
                        comment = comment,
                        onCommentChange = { comment = it }
                    )
                } else {
                    ReadOnlyDetailsTab(song)
                }

                1 -> if (isEditing) {
                    EditableLyricsTab(lyrics = lyrics, onLyricsChange = { lyrics = it })
                } else {
                    ReadOnlyLyricsTab(song)
                }

                2 -> ArtworkTab(
                    artworkUri = song?.artworkUri,
                    pendingArtworkBytes = uiState.pendingArtworkBytes,
                    editable = isEditing && canEdit,
                    onPick = {
                        pickMedia.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.SingleMimeType("image/*")
                            )
                        )
                    },
                    onReset = { viewModel.onEvent(SongInfoEvent.ResetArtwork) },
                    onRemove = { viewModel.onEvent(SongInfoEvent.RemoveArtwork) }
                )
            }
        }
    }
}

/**
 * Read-only details tab displaying all song metadata fields.
 *
 * @param song the song to display; shows a placeholder when null.
 */
@Composable
private fun ReadOnlyDetailsTab(song: Song?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        song?.let { s ->
            InfoRow(label = "Title", value = s.title)
            InfoRow(label = "Artist", value = s.artist)
            InfoRow(label = "Album", value = s.album)
            InfoRow(label = "Album Artist", value = s.albumArtist)
            InfoRow(label = "Genre", value = s.genre)
            InfoRow(label = "Year", value = s.year.toString())
            InfoRow(label = "Track Number", value = s.trackNumber.toString())
            InfoRow(label = "Duration", value = "${s.durationMs / 1000}s")
            InfoRow(label = "Bitrate", value = "${s.bitrate} kbps")
            InfoRow(label = "Sample Rate", value = "${s.sampleRate} Hz")
            InfoRow(label = "Path", value = s.path)
            InfoRow(label = "Comment", value = s.comment)
        } ?: Text("Loading...", style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Editable details tab with text fields for each metadata field.
 *
 * @param title current title value.
 * @param onTitleChange callback when title changes.
 * @param artist current artist value.
 * @param onArtistChange callback when artist changes.
 * @param album current album value.
 * @param onAlbumChange callback when album changes.
 * @param genre current genre value.
 * @param onGenreChange callback when genre changes.
 * @param year current year value.
 * @param onYearChange callback when year changes.
 * @param comment current comment value.
 * @param onCommentChange callback when comment changes.
 */
@Composable
private fun EditableDetailsTab(
    title: String,
    onTitleChange: (String) -> Unit,
    artist: String,
    onArtistChange: (String) -> Unit,
    album: String,
    onAlbumChange: (String) -> Unit,
    genre: String,
    onGenreChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        MetadataField(label = "Title", value = title, onValueChange = onTitleChange)
        MetadataField(label = "Artist", value = artist, onValueChange = onArtistChange)
        MetadataField(label = "Album", value = album, onValueChange = onAlbumChange)
        MetadataField(label = "Genre", value = genre, onValueChange = onGenreChange)
        MetadataField(label = "Year", value = year, onValueChange = onYearChange)
        MetadataField(label = "Comment", value = comment, onValueChange = onCommentChange)
    }
}

/**
 * Read-only lyrics tab displaying the song's embedded lyrics.
 *
 * @param song the song to display; shows a placeholder when no lyrics are available.
 */
@Composable
private fun ReadOnlyLyricsTab(song: Song?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        val lyrics = song?.lyrics
        if (lyrics.isNullOrBlank()) {
            Text(
                text = "No lyrics available.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = lyrics,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Editable lyrics tab with a multi-line text field.
 *
 * @param lyrics current lyrics value.
 * @param onLyricsChange callback when lyrics change.
 */
@Composable
private fun EditableLyricsTab(
    lyrics: String,
    onLyricsChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextField(
            value = lyrics,
            onValueChange = onLyricsChange,
            label = { Text("Lyrics") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 10
        )
    }
}

/**
 * Artwork tab with pick/reset/remove actions.
 *
 * @param artworkUri the current artwork URI string (null if none).
 * @param pendingArtworkBytes optional pending artwork bytes selected from the gallery.
 * @param editable whether the pick/reset/remove actions are enabled.
 * @param onPick callback to launch the gallery picker.
 * @param onReset callback to reset to the embedded MP3 artwork.
 * @param onRemove callback to remove the artwork.
 */
@Composable
private fun ArtworkTab(
    artworkUri: String?,
    pendingArtworkBytes: ByteArray?,
    editable: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayUri = if (pendingArtworkBytes != null) {
        // Let ArtworkPicker handle bytes via data = byteArray
        null
    } else {
        artworkUri
    }

    ArtworkPicker(
        artworkUri = displayUri,
        artworkBytes = pendingArtworkBytes,
        editable = editable,
        onPick = onPick,
        onReset = onReset,
        onRemove = onRemove,
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Permission banner displayed when the app lacks write access while editing.
 *
 * Shows an error-colored card with a message and a "Grant Permission" button
 * that opens the system all-files-access settings screen (Android R+).
 */
@Composable
private fun PermissionBanner() {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "All files access permission is required to edit MP3 metadata and artwork.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                        )
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Grant Permission")
            }
        }
    }
}

/**
 * Displays a single key-value info row in the read-only details tab.
 *
 * @param label the field label (e.g. "Title", "Artist").
 * @param value the field value to display.
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
