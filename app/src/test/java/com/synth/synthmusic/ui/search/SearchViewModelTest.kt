package com.synth.synthmusic.ui.search

import com.synth.synthmusic.data.media.MediaPlaybackManager
import com.synth.synthmusic.data.repository.FakeSongRepository
import com.synth.synthmusic.domain.model.Song
import org.mockito.Mockito.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SearchViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSongRepository
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeSongRepository()
        viewModel = SearchViewModel(repository, mock(MediaPlaybackManager::class.java))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty query returns empty results`() = runTest {
        repository.setSongs(
            listOf(
                Song(id = "1", title = "Test Song", artist = "Artist", album = "Album", albumArtist = "Artist", durationMs = 1000, trackNumber = 1, year = 2024, genre = "", comment = "", path = "/test/1.mp3", uri = "", bitrate = 320, sampleRate = 44100, fileSize = 0, artworkUri = null, rating = 0f, playCount = 0, lastPlayed = null, dateAdded = 0, dateModified = 0, lyrics = null)
            )
        )

        viewModel.onQueryChanged("")
        delay(400) // wait for debounce

        val results = viewModel.results.first()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `search filters songs by title`() = runTest {
        val song1 = Song(id = "1", title = "Hello World", artist = "A", album = "B", albumArtist = "A", durationMs = 1000, trackNumber = 1, year = 2024, genre = "", comment = "", path = "/test/1.mp3", uri = "", bitrate = 320, sampleRate = 44100, fileSize = 0, artworkUri = null, rating = 0f, playCount = 0, lastPlayed = null, dateAdded = 0, dateModified = 0, lyrics = null)
        val song2 = Song(id = "2", title = "Goodbye", artist = "C", album = "D", albumArtist = "C", durationMs = 1000, trackNumber = 1, year = 2024, genre = "", comment = "", path = "/test/2.mp3", uri = "", bitrate = 320, sampleRate = 44100, fileSize = 0, artworkUri = null, rating = 0f, playCount = 0, lastPlayed = null, dateAdded = 0, dateModified = 0, lyrics = null)
        repository.setSongs(listOf(song1, song2))

        viewModel.onQueryChanged("Hello")
        delay(400)

        val results = viewModel.results.first()
        assertEquals(1, results.size)
        assertEquals("Hello World", results[0].title)
    }
}
