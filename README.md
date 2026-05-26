# Synth Music

An offline-first Android music player for local MP3 files. Built with modern Android development practices, Jetpack Compose, and Media3 ExoPlayer.

## Features

- **Local MP3 Playback** — Scans device storage via `MediaStore` and indexes metadata into a local Room database
- **Rich Library Browser** — Browse songs, albums, artists, folders, playlists, favorites, and playback history
- **Background Playback** — Media3 `MediaSessionService` with notification controls and proper foreground service handling
- **Queue Management** — Add, remove, reorder, play-next, and shuffle queue items
- **Audio Effects** — 5-band equalizer, bass boost, loudness enhancement, and virtualizer
- **ReplayGain Support** — Automatic per-track volume adjustment based on ID3 ReplayGain tags
- **Crossfade & Gapless** — Seamless transitions between tracks
- **Playback Speed & Pitch** — Independent speed and pitch control with skip silence
- **Audio Visualizer** — Real-time FFT frequency-bar visualization
- **Waveform Seekbar** — Amplitude envelope seekbar for the currently playing track
- **Bookmarks** — Save timestamp bookmarks within tracks
- **Sleep Timer** — Auto-stop playback after a set duration
- **Metadata Editor** — Edit ID3 tags using JAudioTagger (read/write)
- **Playlist Import/Export** — Share and backup playlists
- **Themes** — Light, dark, and system theme with selectable accent colors (Material 3)
- **Search** — Global search across songs, albums, and artists

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.2.10 |
| UI Toolkit | Jetpack Compose (Material 3) |
| Navigation | Jetpack Navigation Compose with Kotlin Serialization |
| Dependency Injection | Koin |
| Local Database | Room 2.7.1 with KSP |
| Preferences | DataStore Preferences |
| Audio Playback | Media3 ExoPlayer 1.6.1 + MediaSession |
| Image Loading | Coil |
| Metadata | JAudioTagger, MediaMetadataRetriever |

## Architecture

The app follows a **single-activity, feature-first** architecture with **MVVM** and unidirectional data flow:

- **UI Layer** — Stateless Compose screens observing `StateFlow<UiState>` from ViewModels
- **Domain Layer** — Repository interfaces, use cases, and pure data models
- **Data Layer** — Room entities/DAOs, repository implementations, and media playback manager

```
app/src/main/java/com/synth/synthmusic/
├── MainActivity.kt
├── MusicApplication.kt
├── navigation/          # Type-safe routes & NavHost
├── service/             # MediaSessionService for background playback
├── di/                  # Koin modules
├── domain/              # Models, repository interfaces, use cases
├── data/                # Room database, repositories, media engine
└── ui/                  # Feature screens (library, nowplaying, settings, ...)
```

## Build

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Build release APK
./gradlew :app:assembleRelease

# Run unit tests
./gradlew :app:testDebugUnitTest
```

**Requirements:**
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 36
- Min SDK 24 (Android 7.0)

## Permissions

| Permission | Purpose |
|------------|---------|
| `READ_MEDIA_AUDIO` | Read MP3 files (API 33+) |
| `READ_EXTERNAL_STORAGE` | Fallback for older Android (maxSdk 32) |
| `POST_NOTIFICATIONS` | Playback notification (API 33+) |
| `RECORD_AUDIO` | Visualizer audio capture |
| `FOREGROUND_SERVICE` | Background playback service |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media playback foreground type |
| `WAKE_LOCK` | Keep CPU awake during playback |

## Privacy

Synth Music is completely offline. No network permissions are declared, and no data leaves your device.

## License

MIT License — see [LICENSE](LICENSE) for details.
