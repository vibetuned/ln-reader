# Design

## Stack

| Concern | Choice |
| --- | --- |
| Build | AGP 9.2.1, Gradle 9.4.1, JDK 21, Kotlin (AGP-bundled) |
| UI | Jetpack Compose + Material 3, single Activity, Compose Navigation |
| Playback | Media3 / ExoPlayer (`MediaSessionService`) |
| Persistence | Room (current version 5), DataStore Preferences |
| Image loading | Coil 3 |
| EPUB reader | `androidx.webkit` WebView + `WebViewAssetLoader` |
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
│   │                               EmbeddedImageEntity, CollectionEntity, DAOs, LnReaderDatabase
│   ├── model/                     Domain models (Book, Collection, Chapter, …)
│   ├── prefs/                     DataStore wrappers: DownloadPreferences, ViewerPreferences,
│   │                               LibraryPreferences (sort + per-collection manual order),
│   │                               ReaderPreferences (dark + text zoom)
│   └── repo/                      BookRepository, CollectionRepository, PositionRepository,
│                                   CollectionOrdering (shared sort/manual), (+ Mappers)
├── m4b/                           Custom MP4 atom parser (M4bSource, AtomReader, M4bParser)
├── companion/                     EpubBook (unzip + OPF spine), SyncManifest (+ parser)
├── player/                        PlaybackService, PlayerHolder, SleepTimer*, ShakeDetector,
│                                   PostponeReceiver, SleepTimerNotifier, CollectionAdvanceController
└── ui/
    ├── common/                    PlaceholderScreen, appContainer() composable
    ├── library/                   LibraryScreen (library + collection views) + BookDetailSheet
    │                              + LibraryViewModel + ReorderScreen + ReorderViewModel
    ├── navigation/                TopLevelDestination, LnReaderNavGraph, route patterns
    ├── player/                    PlayerScreen + MiniPlayer + ContinueCollectionSheet + ChapterListSheet
    │                              + SpeedSheet + SleepTimerSheet + PlayerViewModel
    ├── reader/                    ReaderScreen + ReaderViewModel (EPUB WebView + beat sync, light/dark + text zoom)
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
    val collectionRepository: CollectionRepository by lazy { … }
    val positionRepository: PositionRepository by lazy { … }
    val playerHolder: PlayerHolder by lazy { … }
    val sleepTimerController: SleepTimerController by lazy { … }
    val downloadPreferences: DownloadPreferences by lazy { … }
    val libraryPreferences: LibraryPreferences by lazy { … }
    val readerPreferences: ReaderPreferences by lazy { … }
    val viewerPreferences: ViewerPreferences by lazy { … }

    // Process-scoped, mutable: guards the "reopen last book once per launch" logic so it fires on
    // a fresh process (cold start or OS-killed restore) but not on config changes.
    var lastBookRestoreHandled = false
}
```

ViewModels are created with `viewModel(factory = SomeViewModel.factory(deps…))` so each one stays testable in isolation.

## Data layer

### Schema (Room v5)

```
books(id PK, uri, title, author, album, durationMs, coverPath, importedAt,
      fileSize, syncKey, isDownloaded, epubPath, syncPath, collectionId)
collections(id PK, name, createdAt)
chapters(id PK auto, bookId FK, orderIndex, title, startMs)
positions(bookId PK, positionMs, updatedAt)
embedded_images(id PK auto, bookId FK, orderIndex, mimeType, cachePath)
```

Migrations (all incremental and non-destructive — real users are on the Play Store, so `fallbackToDestructiveMigration` is only a last-resort net):
- **v1 → v2** added `syncKey TEXT` for a sync feature that was removed; the column is vestigial but kept to avoid a table-rebuild migration.
- **v2 → v3** added `isDownloaded INTEGER NOT NULL DEFAULT 0`. Used by `BookRepository.delete` to decide whether the file at `uri` is a copy we own (delete it) or the user's original source (don't touch).
- **v3 → v4** added `epubPath TEXT` and `syncPath TEXT` — local paths to the optional EPUB and sync-manifest companions.
- **v4 → v5** created the `collections` table and added `books.collectionId TEXT` (nullable; `null` = top-level library). A book belongs to at most one collection; collections don't nest.

### Repositories

- `BookRepository` — `books()` / `topLevelBooks()` (`collectionId IS NULL`) / `booksInCollection(id)` flows, `import(uri, collectionId?, onProgress)`, `delete(id)`, `getDetail(id)`, `bookDetail(id)` flow, plus companion ops `attachEpub` / `attachSync` / `detachEpub` / `detachSync`.
- `CollectionRepository` — `collections()` flow (each `Collection` carries a live book count and up to nine contained cover paths for its tile art, built by `combine`-ing the counts query with a covers query), `collection(id)` flow, `create(name)`, `addBook` / `removeBook`, and `delete(id)` (moves any remaining books back to the top level, then drops the row).
- `PositionRepository` — `observe` / `get` / `save` / `clear` keyed by `bookId`, plus `observeAllPositions()` (bookId → ms map, feeds the per-book library progress bars) and `lastPlayedBookId()` (most-recent existing position — resume-on-launch).

All repositories run their work on `Dispatchers.IO` and bundle multi-table writes in `database.withTransaction { }`.

## Collections

Collections are OS-folder-style groupings, modelled as a single nullable
`books.collectionId` foreign key (no join table, no nesting). A `null` id means
the book sits at the top of the library.

- **Library grid** is a heterogeneous list. `LibraryUiState.entries` is a
  `sealed interface LibraryEntry { CollectionEntry, BookEntry }`; the top-level
  view lists collections (name-sorted) first, then loose books; a collection view
  lists only its books.
- **`LibraryScreen` is reused** for both. A `collectionId` parameter (null =
  top level) selects the view; the two are wired as separate nav routes
  (`library`, `collection?collectionId=…`). Inside a collection the top bar gains
  a back arrow + the collection name + a delete action, and the FAB imports
  straight into the collection instead of showing the Book/Collection menu.
- **Tile art** (`CollectionCoverArt`) draws up to nine contained covers as three
  rows of three, each row overlapping toward the left (leftmost on top via
  `zIndex`) and centred. Cover size is computed for a fixed 3×3 grid from
  `BoxWithConstraints` (not `weight`) so a shelf of 1–2 covers matches a full
  one; empty collections fall back to a folder glyph.
- **Membership** is edited from `BookDetailSheet` — *Add to collection* (a picker
  of existing collections plus a "New collection…" entry) when the book is loose,
  or *Remove from collection* when it's inside one.
- **Delete** offers both outcomes the user asked for: *move books back to the
  library* (clear membership, drop the row) or *delete the books too* (the VM
  deletes each via `BookRepository.delete`, then drops the row). The screen waits
  on a `collectionDeleted` state flag before popping, so the work finishes on the
  still-alive VM scope.
- **Ordering** is centralised in `data/repo/CollectionOrdering.kt`
  (`sortBooksByField` / `applyManualOrder` / `orderCollectionBooks`) — the single
  source of truth shared by the grid, the reorder screen, and end-of-book
  advancing (below).
- **Manual sort** is a *per-collection* mode stored in DataStore (mode flag +
  arranged id order), not a DB column — no migration. Picking **Manual** from a
  collection's sort menu opens `ReorderScreen`: a fixed-height `LazyColumn` where
  long-pressing a drag handle slides a row; the target index follows from the
  accumulated drag offset ÷ row height (swap-and-compensate), and the displaced
  rows use `animateItem()`. Saving writes the id order via
  `LibraryPreferences.setCollectionOrder`.

## Collection advancing

`CollectionAdvanceController` (process-scoped, a peer of `PlayerHolder` /
`SleepTimerController` in `AppContainer`) watches the shared `MediaController` for
a natural end of book — `STATE_ENDED` **while `playWhenReady`** (the guard skips a
paused restore-at-end on launch). If the finished book is in a collection, it
resolves the previous / next book with `orderCollectionBooks` and publishes a
`CollectionEndPrompt`. Because it observes the controller directly, it fires no
matter which screen is showing (even listening via the mini-player).
`MainActivity` renders a global `ContinueCollectionHost` that shows the prompt as
a `ModalBottomSheet` over any screen; tapping a cover calls `continueTo`, which
loads + plays that book. (A finished book's saved position is its end, so
`PlayerViewModel.open` restarts from 0 when the saved position is within
`FINISHED_MARGIN_MS` of the end — otherwise reopening it would instantly re-end.)

## Preferences (DataStore)

Each preferences concern is a small class over **its own** `preferencesDataStore`
file (two classes must never share a file, or DataStore throws "multiple
DataStores active for the same file"). All expose `Flow` getters with defaults +
`suspend` setters; typed options are enums persisted by `.name`.

| Class | File | Stores |
| --- | --- | --- |
| `DownloadPreferences` | `ln_reader_prefs` | download-folder tree URI (null = internal) |
| `ViewerPreferences` | `ln_reader_viewer_prefs` | per-book image backdrop (light/dark/auto) |
| `LibraryPreferences` | `ln_reader_library_prefs` | book sort field + direction; per-collection manual mode + arranged id order (keyed by collection id) |
| `ReaderPreferences` | `ln_reader_reader_prefs` | reader dark mode + text zoom |

Persisted settings are the single source of truth: a ViewModel setter writes to
DataStore, and the same prefs `Flow` (folded into the screen's `combine`) feeds
the value back into UI state.

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

## EPUB companions

A book can have two optional companions, attached/detached per-book from the `BookDetailSheet` and copied into `filesDir/companions/<bookId>/` (`book.epub`, `sync.json`). The combinations:

| EPUB | sync | Result |
| --- | --- | --- |
| ✓ | ✓ | reader with live beat highlighting + scrubber image markers |
| ✓ | ✗ | plain reader |
| ✗ | ✓ | scrubber image markers only |
| ✗ | ✗ | base behavior |

### Sync manifest (`companion/SyncManifest.kt`)

Parses `sync_manifest.json` (via `org.json`) into:
- `beats[]` — each `{ dataBeatId, chapterId, xhtml, startSeconds, endSeconds }`, sorted by start time. `beatAt(positionMs)` binary-searches the last beat whose window has started.
- `images[]` — each `{ src, xhtml, triggerSeconds, ordinal }`. The `ordinal` (array position) is what matches a sync image to an embedded m4b image.

`span_class` / `data_attr` default to `lnvox-beat` / `data-beat-id`.

### Scrubber image markers (player)

`PlayerViewModel.buildMarkers` parses the manifest and, for each sync image, keeps it only if there's an embedded m4b image at the same `orderIndex` — markers are m4b-backed by spec, not EPUB-backed. The scrubber is chapter-relative, so a marker only renders while you're in the chapter containing its `trigger_seconds`, positioned at its chapter-local fraction (inset by the slider thumb radius). Tapping opens the shared `FullScreenImageViewer` at that image index.

### Reader (`companion/EpubBook.kt` + `ui/reader/`)

- `EpubReader.ensureExtracted` unzips the EPUB into `filesDir/epubs/<bookId>/` once (Zip path-traversal guarded, idempotent). `parse` reads `META-INF/container.xml` → OPF, then builds the ordered spine as `{ rootRelativePath, url }`, where `url` is under `https://appassets.androidplatform.net/epub/`.
- `ReaderScreen` hosts a `WebView` whose `WebViewClientCompat.shouldInterceptRequest` delegates to a `WebViewAssetLoader` with an `InternalStoragePathHandler` mounted at `/epub/` → the extracted dir. This serves the XHTML + relative images/CSS over a virtual https origin (no `file://` security issues).
- **Appearance** (persisted app-wide via `ReaderPreferences`): a top-bar toggle switches light / dark, and A− / A+ buttons drive `settings.textZoom` (font scaling, 80–250 %). Dark mode is a `<style>` injected with `evaluateJavascript` on every page load — dark surface + light text, recoloured links, element backgrounds forced transparent — plus the WebView background painted dark. The active-beat highlight carries its own higher-specificity `!important` rule so the theme's transparent-background override doesn't hide it.
- `ReaderViewModel` polls the shared `PlayerHolder` controller every 400 ms. When the controller is playing **this** book, it maps position → active beat (`SyncManifest.beatAt`) → spine page. Highlighting is injected with `evaluateJavascript`: a `.lnvox-active` style plus `querySelector('[data-beat-id="…"]')` → add class + `scrollIntoView`.
- **Auto-follow + manual nav**: follow is on by default (when sync present). Manual prev/next paging turns it off so you can read freely; a contextual **Resume** button (top bar, shown only while follow is off) re-engages it and jumps to the current beat.

Entry points: the player top-bar book icon (enabled when `book.hasEpub`) and the `BookDetailSheet` "Read" button. Route `reader?bookId={bookId}`.

## Import flow

```
SAF picker → BookRepository.import(uri, onProgress)
  1. if isRemoteUri(uri)                            // authority not in com.android.*
       onProgress(Downloading, 0, total)
       downloadToLocation(sourceUri, bookId, fileName,
                         targetFolder = DownloadPreferences.downloadFolderUri)
       → file:///filesDir/downloads/<id>/<name>     // when no folder configured
         OR content://<tree>/document/<created>      // when user picked a folder
     else
       downloadedUri = null                          // parse the original SAF URI in place
  2. onProgress(Parsing)
     M4bSource.open(context, downloadedUri ?: uri) → M4bParser.parse(source) → ParsedM4b
  3. writeImagesToCache(bookId, parsed.images) → filesDir/books/<id>/images/*.{jpg|png}
  4. onProgress(Finalizing)
     transaction { upsert book + chapters + images }
     if downloadedUri != null: releasePersistableUriPermission(originalUri)
```

Remote files are **downloaded before parsing** — the parser reads most of the file regardless, so a single sequential download then a local parse is cheaper than a scattered network parse plus a download. Local files are parsed in place.

Cleanup hooks (`val cleanup = mutableListOf<() -> Unit>()`) run via `try / finally` so a failure at any step removes the partial download / images.

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
- `open(bookId, autoPlay)` is idempotent: if the requested book is already in state, no-op; otherwise load metadata, fetch saved position, and queue `playerHolder.loadBook(book, startPos, playWhenReady = autoPlay)` (or stash it as `pendingLoad`). Tapping a book in the library opens with `autoPlay = true`; resume-on-launch opens paused.

The scrubber works in chapter-local coordinates: the slider's value range is the current chapter's duration; on `onValueChangeFinished` it adds back the chapter start to produce a book-absolute seek. When the book has no chapters the scrubber falls back to whole-book values. Between the two chapter-time labels sits a **whole-book strip** — a ¾-width progress bar plus the remaining time (`Xh Ym`); both derive from `chapterStart + sliderValue` so they track a drag live. The mini-player shows the same two levels as stacked bars (chapter over book).

**Adopting the current book safely.** `PlayerViewModel` follows the controller's current item so re-entering the player mid-playback shows the right book. First-attach adoption is *populate-if-empty* (`replaceExisting = false`) so it can't clobber a book the screen explicitly `open`ed during the race; later `onMediaItemTransition`s adopt with `replaceExisting = true` to follow genuine book changes (e.g. continuing a collection).

**Chapter selector.** The current chapter title and "Chapter N of M" are stacked into one tappable control under the book title that opens the `ChapterListSheet`.

**Mini-player** (`MiniPlayer.kt`). A compact bar `MainActivity` renders just above the `NavigationBar` on every destination except the full player. It reads "now playing" straight off the `MediaController` (metadata title/artist/artwork, `isPlaying`, position) through a `produceState` that adds a `Player.Listener` and polls ~1 s — no extra state holder. Tapping the row opens the full player (`launchSingleTop`); a Read button appears when the current book has an EPUB and you aren't already in the reader.

**Resume on launch.** On a fresh process, `MainActivity` reads `PositionRepository.lastPlayedBookId()` and navigates to the player **paused** at the saved position. The once-per-launch guard is `AppContainer.lastBookRestoreHandled` (process-scoped) rather than `rememberSaveable`, which survives process death and would wrongly skip the reopen after the OS evicts the app.

## Sleep timer

`SleepTimerController` is process-scoped (a peer of `PlayerHolder` in `AppContainer`) and drives playback through the same `MediaController` the UI uses — no extra plumbing across the service boundary.

```
state:          StateFlow<SleepTimerState?>      // running countdown / null
expiredConfig:  StateFlow<SleepTimerConfig?>     // set once the timer fires, until handled
```

- **Time mode** counts elapsed *play* time (not wall time), so manually pausing freezes the countdown.
- **Chapter mode** keys off `currentPosition` against the chapter list pulled from `BookRepository`; pause naturally freezes it because `currentPosition` doesn't advance.
- **Fade-out** is a linear ramp on `controller.volume` over the last `fadeOutSeconds` — user-chosen from Off / 10 / 30 / 60 / 300 s (a discrete slider). `0` disables the fade entirely.

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
  ├── downloads/<bookId>/<filename>            downloaded m4b copies (when DownloadPreferences
  │                                             points at the internal default)
  ├── companions/<bookId>/{book.epub,sync.json} attached EPUB + sync manifest
  └── epubs/<bookId>/…                         extracted EPUB working tree (for the WebView)
```

When the user picks an external download folder, downloads land there instead (one file per book, prefixed with the first 8 chars of the book id so collisions can't happen).

## Navigation

Single `NavHost` with five top-level routes plus parameterized ones:

```
library
collection?collectionId={collectionId}        opens one collection (reuses LibraryScreen)
player?bookId={bookId}&autoPlay={autoPlay}     args optional — null / false via bottom nav
viewer?bookId={bookId}    bookId optional — null when entered via bottom nav
reader?bookId={bookId}    same shape
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
