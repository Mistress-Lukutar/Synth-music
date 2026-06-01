package com.synth.synthmusic.ui.metadata

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.ui.metadata.components.ArtworkPicker
import com.synth.synthmusic.ui.metadata.components.MetadataField
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Metadata editor screen for a single song with Details, Lyrics, and Artwork tabs.
 *
 * @param songId the identifier of the song to edit.
 * @param onNavigateBack callback invoked when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMetadataScreen(
    songId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditMetadataViewModel = koinViewModel { parametersOf(songId) }
) {
    val song by viewModel.song.collectAsStateWithLifecycle()
    val s = song

    var title by remember(s?.id) { mutableStateOf(s?.title ?: "") }
    var artist by remember(s?.id) { mutableStateOf(s?.artist ?: "") }
    var album by remember(s?.id) { mutableStateOf(s?.album ?: "") }
    var genre by remember(s?.id) { mutableStateOf(s?.genre ?: "") }
    var year by remember(s?.id) { mutableStateOf(s?.year?.toString() ?: "") }
    var comment by remember(s?.id) { mutableStateOf(s?.comment ?: "") }
    var lyrics by remember(s?.id) { mutableStateOf(s?.lyrics ?: "") }
    var artworkUri by remember(s?.id) { mutableStateOf(s?.artworkUri ?: "") }

    val tabs = listOf("Details", "Lyrics", "Artwork")
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Edit Metadata") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (s != null) {
                        TextButton(
                            onClick = {
                                viewModel.save(
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    genre = genre,
                                    year = year,
                                    comment = comment,
                                    lyrics = lyrics,
                                    artworkUri = artworkUri
                                )
                                onNavigateBack()
                            }
                        ) {
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
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, titleText ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(titleText) }
                    )
                }
            }

            when (selectedTab) {
                0 -> DetailsTab(
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

                1 -> LyricsTab(
                    lyrics = lyrics,
                    onLyricsChange = { lyrics = it }
                )

                2 -> ArtworkTab(
                    artworkUri = artworkUri,
                    onArtworkUriChange = { artworkUri = it }
                )
            }
        }
    }
}

@Composable
private fun DetailsTab(
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

@Composable
private fun LyricsTab(
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

@Composable
private fun ArtworkTab(
    artworkUri: String,
    onArtworkUriChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ArtworkPicker(
        artworkUri = artworkUri,
        onArtworkUriChange = onArtworkUriChange,
        editable = true,
        modifier = modifier.fillMaxSize()
    )
}
