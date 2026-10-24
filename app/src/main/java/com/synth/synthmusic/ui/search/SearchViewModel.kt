package com.synth.synthmusic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.local.datastore.SearchHistoryStore
import com.synth.synthmusic.data.media.PlaybackRepository
import com.synth.synthmusic.domain.model.Album
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.model.CollectionType
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.AlbumRepository
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.RecentlyPlayedCollectionRepository
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the standalone search screen.
 *
 * Manages a combined catalog of songs, albums, and artists observed from repositories,
 * provides case-insensitive text search over that catalog, tracks recent searches and
 * recently played collections (albums and artists only), and exposes playback commands.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val recentlyPlayedRepository: RecentlyPlayedCollectionRepository,
    private val searchHistoryStore: SearchHistoryStore,
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** In-memory catalog of all songs, albums, and artists. */
    private var catalog: Triple<List<Song>, List<Album>, List<Artist>>? = null

    init {
        // Collect the combined catalog so that filter operations have data to work against.
        combine(
            songRepository.observeAllSongs(),
            albumRepository.observeAllAlbums(),
            artistRepository.observeAllArtists()
        ) { songs, albums, artists ->
            Triple(songs, albums, artists)
        }.onEach { triple ->
            catalog = triple
        }.launchIn(viewModelScope)

        // Playback state subscription — track the currently playing song.
        playbackRepository.playbackState
            .onEach { playback ->
                _uiState.update { it.copy(currentSongId = playback.currentSongId) }
            }
            .launchIn(viewModelScope)

        // Recent collections: observe and filter to ALBUM and ARTIST types only.
        recentlyPlayedRepository.observeRecent()
            .onEach { collections ->
                val filtered = collections.filter { it.type == CollectionType.ALBUM || it.type == CollectionType.ARTIST }
                _uiState.update { it.copy(recentCollections = filtered) }
            }
            .launchIn(viewModelScope)

        // Recent searches: observe search history.
        searchHistoryStore.searchHistory
            .onEach { history ->
                _uiState.update { it.copy(recentSearches = history) }
            }
            .launchIn(viewModelScope)

        // Search pipeline: debounce query changes, then filter the in-memory catalog.
        _uiState
            .debounce(300)
            .flatMapLatest { state ->
                if (state.query.isBlank()) {
                    flowOf(SearchResults(emptyList(), emptyList(), emptyList()))
                } else {
                    flow { emit(withContext(Dispatchers.Default) { filter(state.query) }) }
                }
            }
            .onEach { results ->
                _uiState.update {
                    it.copy(
                        songs = results.songs,
                        albums = results.albums,
                        artists = results.artists
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Filter the in-memory catalog by the given [query].
     *
     * Performs case-insensitive Unicode matching on relevant fields for songs,
     * albums, and artists. Returns results preserving catalog order.
     */
    private fun filter(query: String): SearchResults {
        val catalog = catalog ?: return SearchResults(emptyList(), emptyList(), emptyList())

        val (songs, albums, artists) = catalog

        val filteredSongs = songs.filter { song ->
            song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true) ||
                song.album.contains(query, ignoreCase = true) ||
                song.albumArtist.contains(query, ignoreCase = true)
        }

        val filteredAlbums = albums.filter { album ->
            album.title.contains(query, ignoreCase = true) ||
                album.artist.contains(query, ignoreCase = true)
        }

        val filteredArtists = artists.filter { artist ->
            artist.name.contains(query, ignoreCase = true)
        }

        return SearchResults(filteredSongs, filteredAlbums, filteredArtists)
    }

    /**
     * Handle a search event from the UI.
     */
    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> {
                _uiState.update { it.copy(query = event.query) }
            }
            is SearchEvent.PlaySong -> playSong(event.songId)
            is SearchEvent.HistorySelected -> {
                _uiState.update { it.copy(query = event.query) }
            }
            is SearchEvent.RemoveHistoryEntry -> {
                viewModelScope.launch {
                    searchHistoryStore.removeQuery(event.query)
                }
            }
            is SearchEvent.ClearHistory -> {
                viewModelScope.launch {
                    searchHistoryStore.clear()
                }
            }
        }
    }

    /**
     * Play the song identified by [songId] from the full catalog.
     *
     * Records the current query in search history if it is non-blank.
     */
    private fun playSong(songId: String) {
        viewModelScope.launch {
            val songs = songRepository.observeAllSongs().first()
            val index = songs.indexOfFirst { it.id == songId }
            if (index != -1) {
                playbackRepository.playSongs(songs, index)
            }

            // Record the current query in history if non-blank.
            val currentQuery = _uiState.value.query.trim()
            if (currentQuery.isNotBlank()) {
                searchHistoryStore.addQuery(currentQuery)
            }
        }
    }

    /**
     * Play the specified song next in the queue.
     */
    fun playNext(songId: String) {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId) ?: return@launch
            playbackRepository.playNext(song)
        }
    }

    /**
     * Add the specified song to the end of the queue.
     */
    fun addToQueue(songId: String) {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId) ?: return@launch
            playbackRepository.addToQueue(song)
        }
    }

    /**
     * Search results container holding filtered songs, albums, and artists.
     */
    data class SearchResults(
        val songs: List<Song>,
        val albums: List<Album>,
        val artists: List<Artist>
    )
}
