# Synth Music — Agent Guide

## Project Overview

**Synth Music** is an offline-first Android music player for local MP3 files. It scans device storage via `MediaStore`, indexes metadata into a local Room database, and provides a rich playback experience built around Media3 ExoPlayer.

The app follows a **single-activity, feature-first** architecture with Jetpack Compose for the UI, MVVM for state management, and type-safe navigation.

- **Application ID**: `com.synth.synthmusic`
- **Package**: `com.synth.synthmusic`
- **Language**: Kotlin (100%)
- **UI Language**: English
- **Code Comments / KDoc**: English

---

## Technology Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.2.10 |
| Build System | Gradle (Kotlin DSL) |
| Android Gradle Plugin | 9.2.1 |
| UI Toolkit | Jetpack Compose (Material 3) |
| Navigation | Jetpack Navigation Compose with Kotlin Serialization |
| Dependency Injection | **Koin** (`koin-android`, `koin-androidx-compose`) |
| Local Database | Room 2.7.1 with KSP |
| Preferences | DataStore Preferences |
| Audio Playback | Media3 ExoPlayer 1.6.1 + MediaSession |
| Image Loading | Coil |
| Metadata | JAudioTagger (read/write ID3), `MediaMetadataRetriever` (fallback) |
| Serialization | Kotlinx Serialization |
| Coroutines | Kotlin Coroutines + Flow |

---

## Build Configuration

- **Compile SDK**: 36
- **Target SDK**: 36
- **Min SDK**: 24 (Android 7.0)
- **Java Compatibility**: VERSION_17
- **Compose BOM**: 2026.02.01

### Key Build Files

| File | Purpose |
|------|---------|
| `settings.gradle.kts` | Project structure (`:app` module only), plugin management, repository declarations |
| `build.gradle.kts` (root) | Top-level plugins (android-application, kotlin-compose, kotlin-serialization, ksp) |
| `gradle/libs.versions.toml` | Centralized version catalog for all dependencies |
| `app/build.gradle.kts` | App-level build config, dependencies, ProGuard, Compose feature flag |
| `gradle.properties` | JVM args, Kotlin code style (`official`) |

### Build Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Build release APK
./gradlew :app:assembleRelease

# Run unit tests
./gradlew :app:testDebugUnitTest

# Run instrumented tests (requires connected device/emulator)
./gradlew :app:connectedDebugAndroidTest

# Clean build
./gradlew clean
```

---

## Architecture

### Single-Activity, Feature-First Packaging

```
app/src/main/java/com/synth/synthmusic/
├── MainActivity.kt                 # Single entry point; sets Compose content
├── MusicApplication.kt             # Application class; initializes Koin
├── navigation/
│   ├── AppNavigation.kt            # NavHost wiring all routes
│   └── Routes.kt                   # @Serializable route definitions
├── service/
│   └── PlaybackService.kt          # Media3 MediaSessionService for background playback
├── di/
│   └── AppModule.kt                # Koin modules (database, repositories, ViewModels)
├── domain/
│   ├── model/                      # Pure data classes (Song, Album, Artist, Playlist, etc.)
│   ├── repository/                 # Repository interfaces exposing Flow<T>
│   └── usecase/                    # Use cases (ScanMusicUseCase, etc.)
├── data/
│   ├── local/
│   │   ├── database/               # Room entities, DAOs, converters, mappers
│   │   └── datastore/              # DataStore-backed settings
│   ├── repository/                 # Repository implementations
│   └── media/                      # ExoPlayer wrapper, audio effects, waveform generator
└── ui/
    ├── theme/                      # Color, Typography, Theme composable
    ├── library/                    # Main screen with tabs
    ├── nowplaying/                 # Full-screen player
    ├── queue/                      # Playback queue
    ├── search/                     # Global search
    ├── equalizer/                  # 5-band EQ
    ├── playlists/                  # Playlist CRUD
    ├── folders/                    # Folder browser
    ├── albums/                     # Album detail
    ├── artists/                    # Artist detail
    ├── bookmarks/                  # Timestamp bookmarks
    ├── metadata/                   # Song info / edit metadata
    ├── settings/                   # App settings
    ├── visualizer/                 # Audio visualizer
    ├── sleeptimer/                 # Sleep timer dialog
    ├── share/                      # Share bottom sheets
    └── downloads/                  # Download manager screen
```

### MVVM + Unidirectional Data Flow

- **Screens** are stateless `@Composable` functions that observe `StateFlow<UiState>` from a `ViewModel`.
- **ViewModels** expose `uiState: StateFlow<UiState>` and accept events via `onEvent(event: ScreenEvent)`.
- **Events** are defined as `sealed class` (e.g., `LibraryEvent`, `NowPlayingEvent`).
- **UiState** is a `data class` holding all UI-relevant state.
- ViewModels use `viewModelScope` to launch coroutines.

### Repository Pattern

- Domain-layer `Repository` interfaces return `Flow<T>` for observation and `suspend` functions for one-shot operations.
- Data-layer `RepositoryImpl` classes delegate to Room DAOs and map `Entity` → `Domain Model` via extension functions (`toDomain()`, `toEntity()`).

### Dependency Injection (Koin)

Koin is used **instead of Hilt**.

- `MusicApplication.onCreate()` calls `startKoin { modules(appModule, dataModule) }`.
- `AppModule.kt` defines:
  - `single` for singletons (database, DAOs, `MediaPlaybackManager`, repositories)
  - `viewModel { ... }` for ViewModel bindings; some ViewModels accept navigation arguments via parameter injection (e.g., `viewModel { (playlistId: Long) -> PlaylistDetailViewModel(...) }`).
- In Compose, inject ViewModels with `koinViewModel()`.

---

## Navigation

Type-safe routes are defined in `navigation/Routes.kt` using Kotlin Serialization (`@Serializable`):

```kotlin
@Serializable object LibraryRoute          // Start destination
@Serializable object NowPlayingRoute
@Serializable object QueueRoute
@Serializable object SearchRoute
@Serializable object EqualizerRoute
@Serializable object BookmarksRoute
@Serializable object VisualizerRoute
@Serializable object SettingsRoute
@Serializable object DownloadsRoute
@Serializable data class SongInfoRoute(val songId: String)
@Serializable data class EditMetadataRoute(val songId: String)
@Serializable data class BatchEditRoute(val songIds: List<String>)
@Serializable data class PlaylistDetailRoute(val playlistId: Long)
@Serializable data class FolderDetailRoute(val folderPath: String)
@Serializable data class AlbumDetailRoute(val albumTitle: String, val albumArtist: String)
@Serializable data class ArtistDetailRoute(val artistName: String)
```

**Rule**: Do **not** pass `NavController` into composables. Pass lambda callbacks (`onNavigateToX`) from `AppNavigation.kt`.

---

## Database & Persistence

### Room Database (`AppDatabase`)

- **Version**: 5
- **Entities**: `SongEntity`, `AlbumEntity`, `ArtistEntity`, `PlaylistEntity`, `PlaylistSongEntity`, `BookmarkEntity`, `EqPresetEntity`, `PlaybackStateEntity`, `WaveformDataEntity`
- **Schema export**: disabled (`exportSchema = false`)
- **Migration strategy**: `fallbackToDestructiveMigration(dropAllTables = true)` in `AppModule.kt`
- **KSP** is used for compile-time code generation (`ksp(libs.androidx.room.compiler)`).

### DataStore (`SettingsDataStore`)

- Backed by `preferencesDataStore(name = "settings")`.
- Exposes `Flow<AppSettings>`.
- Contains theme, accent color, fade duration, replay gain, EQ, playback speed/pitch, etc.

---

## Audio Engine

### Playback (`MediaPlaybackManager`)

- Wraps Media3 `ExoPlayer`.
- Manages queue as `StateFlow<List<Song>>`.
- Handles play/pause, seek, shuffle, repeat, fade (volume ramping), playback speed/pitch.
- Reads `ReplayGain` tags and adjusts `player.volume` per track.
- Persists playback state (current song, position, queue, repeat/shuffle) to Room `PlaybackStateEntity` for process-death recovery.
- **Do not release the player in `ViewModel.onCleared()`** — lifecycle is managed by `PlaybackService`.

### Background Service (`PlaybackService`)

- Extends `MediaSessionService` (Media3).
- Declared in `AndroidManifest.xml` with `foregroundServiceType="mediaPlayback"`.
- Handles notification controls and task-removal behavior.

### Permissions

| Permission | Purpose |
|------------|---------|
| `READ_MEDIA_AUDIO` | Read MP3 files (API 33+) |
| `READ_EXTERNAL_STORAGE` | Fallback for older Android (maxSdk 32) |
| `POST_NOTIFICATIONS` | Playback notification (API 33+) |
| `RECORD_AUDIO` | Visualizer audio capture |
| `FOREGROUND_SERVICE` | Background playback service |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media playback foreground type |
| `WAKE_LOCK` | Keep CPU awake during playback |

---

## Theme & Styling

- **Material 3** with custom accent colors: Yellow, Green, Blue, Red, Purple, Orange.
- Dark/Light/System modes supported.
- Dynamic color is optional (`dynamicColor` param) and only active on Android 12+.
- Edge-to-edge enabled in `MainActivity`.
- Status bar / navigation bar appearance synced with theme in `SynthMusicTheme`.
- Colors defined in `ui/theme/Color.kt`; typography in `ui/theme/Type.kt`.

---

## Code Style Guidelines

1. **Language**: All code, comments, KDoc, and string resources are in **English**.
2. **KDoc**: Every public class, interface, function, and Composable has a KDoc block describing its purpose.
3. **UI Pattern**: `Screen` composable + `ViewModel` + `UiState` data class + `Event` sealed class.
4. **Stateless Composables**: Screens receive navigation lambdas (`onNavigateToX`) and delegate all logic to ViewModels.
5. **Coroutines**: Use `Dispatchers.IO` for file I/O, database writes, and JAudioTagger operations. Collect flows with `.launchIn(viewModelScope)`.
6. **Lists**: Use `LazyColumn` / `LazyVerticalGrid` with `key = { item.id }`.
7. **No blocking calls on Main Thread**.
8. **No hardcoded colors**; derive from `MaterialTheme.colorScheme`.
9. **Repository naming**: Interface `SongRepository`, implementation `SongRepositoryImpl`.
10. **Entity mappers**: `SongEntity.toDomain()` and `Song.toEntity()` in `SongEntityMappers.kt`.

---

## Testing

- **Unit tests**: JUnit 4 (`app/src/test/`)
- **Instrumented tests**: AndroidJUnit4 + Espresso + Compose UI Test (`app/src/androidTest/`)
- **Coroutines test**: `kotlinx-coroutines-test` is on the classpath.
- Current test coverage is minimal (`ExampleUnitTest`, `ConvertersTest`).

```bash
# Run all tests
./gradlew test

# Run unit tests only
./gradlew :app:testDebugUnitTest
```

---

## Security & Privacy Considerations

- The app only reads local audio files; no network permissions are declared.
- JAudioTagger operates on file paths obtained from `MediaStore`. On API 30+, broad file access may require `MANAGE_EXTERNAL_STORAGE` if metadata editing across all directories is needed.
- ID3 tag writes should operate on a file copy first, then replace atomically, to prevent corruption.
- Playback state is persisted locally in Room; no data leaves the device.

---

## Development Notes for Agents

- **The project uses Koin, not Hilt.** Do not add Hilt dependencies or `@HiltAndroidApp` / `@Inject` annotations.
- **Room schema is not exported.** Migrations are not implemented; `fallbackToDestructiveMigration` is active.
- **Player lifecycle** is tied to `PlaybackService`, not to any ViewModel or Activity.
- **Type-safe navigation** uses Kotlin Serialization; add `@Serializable` to new routes and wire them in `AppNavigation.kt`.
- **Feature-first packaging**: place new screens under `ui/<feature>/`, not `ui/screens/<feature>`.
- When adding a new ViewModel, register it in `di/AppModule.kt` with `viewModel { ... }`.
- When adding a new Room entity, add it to `AppDatabase.entities`, create a DAO, and expose it via `AppModule.kt`.
