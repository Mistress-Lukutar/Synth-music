package com.synth.synthmusic.ui.playlists

import com.synth.synthmusic.data.repository.FakePlaylistRepository
import com.synth.synthmusic.domain.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePlaylistRepository
    private lateinit var viewModel: PlaylistViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakePlaylistRepository()
        viewModel = PlaylistViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `create playlist adds to list`() = runTest {
        viewModel.openCreateDialog()
        assertTrue(viewModel.showCreateDialog.first())

        viewModel.createPlaylist("My Playlist")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.showCreateDialog.first())
        val playlists = viewModel.playlists.first()
        assertEquals(1, playlists.size)
        assertEquals("My Playlist", playlists[0].name)
    }

    @Test
    fun `delete playlist removes from list`() = runTest {
        repository.setPlaylists(listOf(Playlist(id = 1, name = "Old", createdAt = 0, songCount = 0)))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deletePlaylist(1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.playlists.first().isEmpty())
    }
}
