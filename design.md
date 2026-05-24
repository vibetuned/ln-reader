# Design

## Stack

| Concern | Choice |
| --- | --- |
| Build | AGP 9.2.1, Gradle 9.4.1, JDK 21, Kotlin (AGP-bundled) |
| UI | Jetpack Compose + Material 3, single Activity, Compose Navigation |
| Playback | Media3 / ExoPlayer (`MediaSessionService`) |
| Persistence | Room (current version 3), DataStore Preferences |
| Image loading | Coil 3 |
| DI | Manual — a single `AppContainer` exposes lazy singletons |
| Module layout | Single `:app` module, package-by-feature |

## Package layout

```
com.vibetuned.ln_reader
├── LnReaderApplication            owns the AppContainer
├── MainActivity                   sets Compose content, requests POST_NOTIFICATIONS
├── di/AppContainer                lazy singletons: DB, repos, parser, player, sleep timer, prefs
├── data/
│   ├── db/                        Room: BookEntity, ChapterEntity, PositionEntity,
│   │                               EmbeddedImageEntity, DAOs, LnReaderDatabase
│   ├── model/                     Domain models (Book, Chapter, …)
│   ├── prefs/DownloadPreferences  DataStore wrapper for the download-folder tree URI
│   └── repo/                      BookRepository, PositionRepository (+ Mappers)
├── m4b/                           Custom MP4 atom parser (M4bSource, AtomReader, M4bParser)
├── player/                        PlaybackService, PlayerHolder, SleepTimer*, ShakeDetector,
│                                   PostponeReceiver, SleepTimerNotifier
└── ui/
    ├── common/                    PlaceholderScreen, appContainer() composable
    ├── library/                   LibraryScreen + BookDetailSheet + LibraryViewModel
    ├── navigation/                TopLevelDestination, LnReaderNavGraph, route patterns
    ├── player/                    PlayerScreen + ChapterListSheet + SpeedSheet
    │                              + SleepTimerSheet + PlayerViewModel
    ├── settings/                  SettingsScreen + SettingsViewModel
    ├── theme/                     Color / Theme / Type (Material 3 dynamic color)
    ├── timer/                     TimerScreen + TimerControls + TimerViewModel
    └── viewer/                    ViewerScreen + FullScreenImageViewer + ViewerViewModel
```

## DI: AppContainer

There is no Hilt. Hilt 2.56.2 (the latest release at the time of writing) still uses AGP 8's `BaseExtension`, which AGP 9 removed, so the plugin fails to apply. The workaround is a tiny container instantiated in `LnReaderApplication.onCreate()` and reached from Compose via `appContainer()`:

```kotlin
class AppContainer(context: Context) {
    val database: LnReaderDatabase by lazy { … }
    val bookRepository: BookRepository by lazy { … }
    val playerHolder: PlayerHolder by lazy { … }
    val sleepTimerController: SleepTimerController by lazy { … }
    val downloadPreferences: DownloadPreferences by lazy { … }
    // …
}
```

ViewModels are created with `viewModel(factory = SomeViewModel.factory(deps…))` so each one stays testable in isolation.

## Data layer

### Schema (Room v3)

```
books(id PK, uri, title, author, album, durationMs, coverPath, importedAt,
      fileSize, syncKey, isDownloaded)
chapters(id PK auto, bookId FK, orderIndex, title, startMs)
positions(bookId PK, positionMs, updatedAt)
embedded_images(id PK auto, bookId FK, orderIndex, mimeType, cachePath)
```

Migrations:
- **v1 → v2** added `syncKey TEXT` for a sync feature that was removed; the column is vestigial but kept to avoid a v2→v3 table-rebuild migration.
- **v2 → v3** added `isDownloaded INTEGER NOT NULL DEFAULT 0`. Used by `BookRepository.delete` to decide whether the file at `uri` is a copy we own (delete it) or the user's original source (don't touch).

### Repositories

- `BookRepository` — `books()` flow, `import(uri, onProgress)`, `delete(id)`, `getDetail(id)`, `bookDetail(id)` flow.
- `PositionRepository` — observe / get / save / clear, all keyed by `bookId`.

Both repositories run their work on `Dispatchers.IO` and bundle multi-table writes in `database.withTransaction { }`.

## M4B parser

Lives in `m4b/` and depends on no third-party library — `jaudiotagger` is JVM-targeted and flaky on Android.

- `M4bSource` — random-access reader over a SAF `Uri`. Opens a `ParcelFileDescriptor`, exposes `readAt(offset, length)`. Closeable.
- `AtomReader` — walks the MP4 box tree, handling the 32-bit / 64-bit / "to end" length encodings and the `meta` full-box prefix.
- `M4bParser` — high-level extractor that returns `ParsedM4b(title, author, album, durationMs, chapters, images)`:
  - Duration from `moov/mvhd` (handles both version 0 and 1 layouts).
  - Title / author / album from `moov/udta/meta/ilst/{©nam,©ART,©alb}` data atoms.
  - Cover and embedded illustrations from every `data` child of `covr`. Reads the type indicator (13 = JPEG, 14 = PNG); sniffs magic bytes when the type is missing.
  - Chapters from Nero `chpl`. The header layout varies between tools, so the parser tries the common 9-byte header (`1 version + 3 flags + 4 reserved + 1 count`) first and falls back to a 5-byte header (`1 version + 3 flags + 1 count`) if the chapter table overflows.

The parser only reads metadata bytes, so it stays fast even over a slow Drive connection. The full audio is only read during the download phase (next section).

## Import flow

```
SAF picker → BookRepository.import(uri, onProgress)
  1. onProgress(Parsing)
     M4bSource.open(context, uri) → M4bParser.parse(source) → ParsedM4b
  2. writeImagesToCache(bookId, parsed.images) → filesDir/books/<id>/images/*.{jpg|png}
  3. if isRemoteUri(uri)                           // authority not in com.android.*
       onProgress(Downloading, 0, total)
       downloadToLocation(sourceUri, bookId, fileName,
                         targetFolder = DownloadPreferences.downloadFolderUri)
       → file:///filesDir/downloads/<id>/<name>    // when no folder configured
         OR content://<tree>/document/<created>     // when user picked a folder
       releasePersistableUriPermission(originalUri)
     else
       use the original SAF URI in place
  4. onProgress(Finalizing)
     transaction { upsert book + chapters + images }
```

Cleanup hooks (`val cleanup = mutableListOf<() -> Unit>()`) run via `try / finally` so a failure at any step removes the partial images / download.

The remote/local heuristic is the URI authority: anything not under `com.android.*` is treated as remote and downloaded. This catches Drive, OneDrive, Dropbox, Box, and friends without an explicit allowlist.

## Player

Lives in two halves:

### Service half (`player/PlaybackService.kt`)

`MediaSessionService` with an `ExoPlayer` configured for spoken-word audio (`C.AUDIO_CONTENT_TYPE_SPEECH`, `setHandleAudioBecomingNoisy(true)`). On every play / pause transition it saves the current position to `PositionRepository`; while playing, a 5 s loop saves periodically. The `onTaskRemoved` override tears the service down only if nothing is loaded or playback is paused — so swiping the app away while listening keeps the audio going.

### UI half (`ui/player/`)

The UI never talks to the service or the player directly. Instead `PlayerHolder` (process-scoped, lives in `AppContainer`) holds a `ListenableFuture<MediaController>` that's connected once and reused everywhere. The `MediaController.controller: StateFlow<MediaController?>` flips from null to non-null when the session binding completes.

`PlayerViewModel` is the only thing that holds player-screen state. Its lifecycle:

- On init it subscribes to `playerHolder.controller`. When a controller appears it:
  - Applies any `pendingLoad` queued before the controller was ready.
  - **Adopts** the controller's `currentMediaItem.mediaId` if the VM has no book set yet (so the Player tab shows the right thing when re-entered mid-playback).
  - Installs a `Player.Listener` that maps `onIsPlayingChanged`, `onPlaybackParametersChanged`, `onPlaybackStateChanged` (incl. `STATE_BUFFERING`), and `onMediaItemTransition` into UI state.
- A 250 ms polling loop reads `currentPosition` / `isPlaying` into the state. Position polling is cheaper and more responsive than waiting for `onPositionDiscontinuity`.
- `open(bookId)` is idempotent: if the requested book is already in state, no-op; otherwise load metadata, fetch saved position, queue `playerHolder.loadBook(book, startPos)` (or stash it as `pendingLoad`).

The scrubber works in chapter-local coordinates: the slider's value range is the current chapter's duration; on `onValueChangeFinished` it adds back the chapter start to produce a book-absolute seek. When the book has no chapters the scrubber falls back to whole-book values.

## Sleep timer

`SleepTimerController` is process-scoped (a peer of `PlayerHolder` in `AppContainer`) and drives playback through the same `MediaController` the UI uses — no extra plumbing across the service boundary.

```
state:          StateFlow<SleepTimerState?>      // running countdown / null
expiredConfig:  StateFlow<SleepTimerConfig?>     // set once the timer fires, until handled
```

- **Time mode** counts elapsed *play* time (not wall time), so manually pausing freezes the countdown.
- **Chapter mode** keys off `currentPosition` against the chapter list pulled from `BookRepository`; pause naturally freezes it because `currentPosition` doesn't advance.
- **Fade-out** is a linear ramp on `controller.volume` over the last 10 s.

When the timer fires:
1. `controller.pause()`, restore volume to 1.
2. `state = null`, `expiredConfig = lastConfig`.
3. `SleepTimerNotifier.postExpired(config)` builds a notification with `Postpone` / `Dismiss` actions; `PostponeReceiver` routes both back into the controller.
4. `ShakeDetector.start()` registers a sensor listener (prefers `TYPE_LINEAR_ACCELERATION`, falls back to `TYPE_ACCELEROMETER` with a low-pass gravity filter). Magnitude > 13 m/s² with a 1.2 s cooldown counts as a shake → `postpone()`.

`postpone()` clears the expired state, calls `controller.play()`, and re-arms the same config. `dismissExpired()` clears state + notification + sensor without restarting.

## Storage layout

```
/data/data/com.vibetuned.ln_reader/files/
  ├── books/<bookId>/images/<idx>.{jpg|png}    parsed embedded images (always internal)
  └── downloads/<bookId>/<filename>            downloaded m4b copies (when DownloadPreferences
                                                points at the internal default)
```

When the user picks an external download folder, downloads land there instead (one file per book, prefixed with the first 8 chars of the book id so collisions can't happen).

## Navigation

Single `NavHost` with five top-level routes plus two parameterized ones:

```
library
player?bookId={bookId}    bookId optional — null when entered via bottom nav
viewer?bookId={bookId}    same shape
timer
settings
```

`MainActivity` renders a Material 3 `NavigationBar`; the selected state matches each tab against `currentDestination.route.substringBefore('?')` so the optional-arg routes still highlight correctly.

## AGP 9 quirks worth remembering

- **No `org.jetbrains.kotlin.android` plugin.** AGP 9 registers its own `kotlin {}` extension; applying the standalone plugin fails.
- **No `kotlinOptions { jvmTarget = … }`.** Removed in AGP 9. Use `kotlin { jvmToolchain(17) }` at the top level of `app/build.gradle.kts`.
- **`android.disallowKotlinSourceSets=false`** in `gradle.properties` is required while KSP and AGP 9 are still aligning — KSP adds Kotlin source sets the way AGP 9 currently rejects.
- **No Hilt.** Hilt 2.56.2 uses the removed `BaseExtension`; the project uses manual DI ([AppContainer](#di-appcontainer)). Revisit when Google ships an AGP-9-compatible release.

## Decisions retired

- **Hilt** — see above.
- **Google Drive sync (folder-tree based)** — Drive's `DocumentsProvider` no longer exposes `ACTION_OPEN_DOCUMENT_TREE`, so the system folder picker doesn't surface Drive folders.
- **Google Drive sync (single positions.json)** — built and removed in the same session after first-sync merge bugs and trust issues. The `Book.syncKey` column is the only residue.
- **Streaming playback from cloud URIs** — first cut played directly from Drive via ContentResolver; UX was bad (no visual feedback, frequent buffering). Replaced with eager download-on-import.

If sync is ever revisited, the user's preferences (no GCP project, no OAuth, SAF-only) still apply.
