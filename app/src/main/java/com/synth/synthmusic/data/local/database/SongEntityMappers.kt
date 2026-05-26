package com.synth.synthmusic.data.local.database

import com.synth.synthmusic.domain.model.Song

/**
 * Convert a database entity to a domain model.
 */
fun SongEntity.toDomain(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    genre = genre,
    comment = comment,
    path = path,
    uri = uri,
    bitrate = bitrate,
    sampleRate = sampleRate,
    fileSize = fileSize,
    artworkUri = artworkUri,
    rating = rating,
    playCount = playCount,
    lastPlayed = lastPlayed,
    dateAdded = dateAdded,
    dateModified = dateModified,
    lyrics = lyrics
)

/**
 * Convert a domain model to a database entity.
 */
fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    genre = genre,
    comment = comment,
    path = path,
    uri = uri,
    bitrate = bitrate,
    sampleRate = sampleRate,
    fileSize = fileSize,
    artworkUri = artworkUri,
    rating = rating,
    playCount = playCount,
    lastPlayed = lastPlayed,
    dateAdded = dateAdded,
    dateModified = dateModified,
    lyrics = lyrics
)
