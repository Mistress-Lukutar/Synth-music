package com.synth.synthmusic.domain.usecase

import com.synth.synthmusic.data.repository.FakeSongRepository
import com.synth.synthmusic.domain.model.Song
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Unit tests for [UpdateMetadataUseCase].
 */
class UpdateMetadataUseCaseTest {

    private lateinit var repository: FakeSongRepository
    private lateinit var useCase: UpdateMetadataUseCase

    @Before
    fun setup() {
        repository = FakeSongRepository()
        useCase = UpdateMetadataUseCase(repository, mock(WriteArtworkToMp3UseCase::class.java))
    }

    @Test
    fun `update metadata changes song fields`() = runTest {
        val song = Song(
            id = "1",
            title = "Old Title",
            artist = "Old Artist",
            album = "Old Album",
            albumArtist = "Old Artist",
            durationMs = 1000,
            trackNumber = 1,
            year = 2020,
            genre = "Rock",
            comment = "",
            path = "/test/1.mp3",
            uri = "",
            bitrate = 320,
            sampleRate = 44100,
            fileSize = 0,
            artworkUri = null,
            rating = 0f,
            playCount = 0,
            lastPlayed = null,
            dateAdded = 0,
            dateModified = 0,
            lyrics = null
        )
        repository.setSongs(listOf(song))

        useCase(
            song = song,
            title = "New Title",
            artist = "New Artist",
            year = "2024"
        )

        val updated = repository.getSongById("1")
        assertEquals("New Title", updated?.title)
        assertEquals("New Artist", updated?.artist)
        assertEquals(2024, updated?.year)
    }
}
