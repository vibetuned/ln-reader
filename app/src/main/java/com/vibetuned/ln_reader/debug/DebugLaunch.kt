package com.vibetuned.ln_reader.debug

import android.content.Intent
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.vibetuned.ln_reader.BuildConfig
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.model.ReadLogEntry
import com.vibetuned.ln_reader.data.model.ReadLogKind
import com.vibetuned.ln_reader.di.AppContainer
import com.vibetuned.ln_reader.player.SleepTimerConfig
import com.vibetuned.ln_reader.ui.navigation.PlayerRoute
import com.vibetuned.ln_reader.ui.navigation.ReaderRoute
import com.vibetuned.ln_reader.ui.navigation.TopLevelDestination
import com.vibetuned.ln_reader.ui.navigation.ViewerRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Launch hooks for scripted smoke tests — the Android side of the iOS `-autoImport` / `-openTitle`
 * family, read from the starting Intent's extras. Debug builds only ([BuildConfig.DEBUG]); a
 * release build parses nothing.
 *
 * ```
 * adb shell am start -n com.vibetuned.ln_reader/.MainActivity \
 *     --es  autoImport "/sdcard/Android/data/com.vibetuned.ln_reader/files/books/book.m4b" \
 *     --ez  autoPlay true \
 *     --es  openTitle "A Certain" \
 *     --ei  seekTo 120 \
 *     --ez  play true
 * ```
 *
 * | extra | type | effect |
 * | --- | --- | --- |
 * | `autoImport` | string / string[] (`--esa a,b`) | import each path (m4b or epub); an m4b picks up a sibling `*.epub` and `sync_manifest.json` |
 * | `autoPlay` | bool | open the first imported book playing |
 * | `testCollectionAdvance` | bool | put the imports in a "Test Shelf", open the first, seek to 20 s before its end |
 * | `openTitle` | string | open the library book whose title starts with this (case-insensitive) — the player, or the reader for an EPUB-only book |
 * | `readTitle` | string | open the reader for the matching book |
 * | `seekTo` | int (seconds) | seek the opened book |
 * | `play` | bool | start playback of the opened book |
 * | `showReader` | bool | open the reader for the opened book |
 * | `showImages` | bool | open the image viewer for the opened book |
 * | `armTimer` | int (minutes) | arm the sleep timer and show Settings (where the timer lives) |
 * | `armChapterTimer` | bool | arm an end-of-chapter timer and show Settings |
 * | `textZoom` | int (percent) | set reader text zoom |
 * | `readerDark` | bool | set reader dark mode |
 * | `readerSearch` | string | run this search once the reader opens |
 * | `readerPage` | int | turn the reader to this 1-based page once it opens (exercises the page mark) |
 * | `seedStats` | bool | replace the usage log with generated sessions across the library (see [seedStats]) |
 * | `seedDays` | int | how far back to generate, default 120 |
 * | `tab` | library / player / images / settings (`timer` is an alias) | end on this tab |
 *
 * Files must be somewhere the app can read without a picker; `adb push` to the app's external
 * files dir (`/sdcard/Android/data/com.vibetuned.ln_reader/files/…`) is the reliable spot.
 */
data class DebugLaunchRequest(
    val autoImport: List<String>,
    val autoPlay: Boolean,
    val testCollectionAdvance: Boolean,
    val openTitle: String?,
    val readTitle: String?,
    val seekToSeconds: Int?,
    val play: Boolean,
    val showReader: Boolean,
    val showImages: Boolean,
    val armTimerMinutes: Int?,
    val armChapterTimer: Boolean,
    val textZoom: Int?,
    val readerDark: Boolean?,
    val readerSearch: String?,
    val readerPage: Int?,
    val seedStats: Boolean,
    val seedDays: Int?,
    val tab: String?
) {
    val isEmpty: Boolean
        get() = autoImport.isEmpty() && !autoPlay && !testCollectionAdvance && openTitle == null &&
            readTitle == null && seekToSeconds == null && !play && !showReader && !showImages &&
            armTimerMinutes == null && !armChapterTimer && textZoom == null && readerDark == null &&
            readerSearch == null && readerPage == null && !seedStats && seedDays == null &&
            tab == null
}

object DebugLaunch {

    /** Null outside debug builds, or when the Intent carries none of the extras. */
    fun parse(intent: Intent?): DebugLaunchRequest? {
        if (!BuildConfig.DEBUG) return null
        val extras = intent?.extras ?: return null
        val request = DebugLaunchRequest(
            autoImport = extras.getStringArray("autoImport")?.toList()
                ?: extras.getString("autoImport")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList(),
            autoPlay = extras.getBoolean("autoPlay", false),
            testCollectionAdvance = extras.getBoolean("testCollectionAdvance", false),
            openTitle = extras.getString("openTitle"),
            readTitle = extras.getString("readTitle"),
            seekToSeconds = extras.takeIf { it.containsKey("seekTo") }?.getInt("seekTo"),
            play = extras.getBoolean("play", false),
            showReader = extras.getBoolean("showReader", false),
            showImages = extras.getBoolean("showImages", false),
            armTimerMinutes = extras.takeIf { it.containsKey("armTimer") }?.getInt("armTimer"),
            armChapterTimer = extras.getBoolean("armChapterTimer", false),
            textZoom = extras.takeIf { it.containsKey("textZoom") }?.getInt("textZoom"),
            readerDark = extras.takeIf { it.containsKey("readerDark") }?.getBoolean("readerDark"),
            readerSearch = extras.getString("readerSearch"),
            readerPage = extras.takeIf { it.containsKey("readerPage") }?.getInt("readerPage"),
            seedStats = extras.getBoolean("seedStats", false),
            seedDays = extras.takeIf { it.containsKey("seedDays") }?.getInt("seedDays"),
            tab = extras.getString("tab")
        )
        return request.takeUnless { it.isEmpty }
    }

    /**
     * Phase 1 — before resume-on-launch: imports, so an `openTitle` in the same launch can find the
     * book, and the collection-advance scenario (which claims the restore slot for itself).
     */
    suspend fun runImports(request: DebugLaunchRequest, container: AppContainer) {
        val imported = mutableListOf<Book>()
        for (path in request.autoImport) {
            val file = File(path)
            val result = container.bookRepository.import(file.toUri())
            val book = result.getOrElse {
                android.util.Log.w(TAG, "autoImport failed for $path", it)
                continue
            }
            android.util.Log.i(TAG, "autoImport: imported ${book.title}")
            // ln-vox output layout: book.m4b next to its *.epub and sync_manifest.json.
            if (book.hasAudio) {
                val siblings = file.parentFile?.listFiles().orEmpty()
                siblings.firstOrNull { it.extension.equals("epub", ignoreCase = true) }?.let {
                    container.bookRepository.attachEpub(book.id, it.toUri())
                }
                siblings.firstOrNull { it.name == "sync_manifest.json" }?.let {
                    container.bookRepository.attachSync(book.id, it.toUri())
                }
            }
            imported += book
        }
        if (request.seedStats) seedStats(container, request.seedDays ?: DEFAULT_SEED_DAYS)

        val first = imported.firstOrNull() ?: return

        if (request.testCollectionAdvance) {
            val collectionId = container.collectionRepository.create("Test Shelf")
            imported.forEach { container.collectionRepository.addBook(it.id, collectionId) }
            android.util.Log.i(TAG, "autoImport: created Test Shelf with ${imported.size} books")
            container.lastBookRestoreHandled = true
            openInPlayer(container, first, startMs = (first.durationMs - 20_000L).coerceAtLeast(0L), play = true)
            return
        }
        if (request.autoPlay && first.hasAudio) {
            container.lastBookRestoreHandled = true
            openInPlayer(container, first, startMs = null, play = true)
        }
    }

    /** Phase 2 — after resume-on-launch: open/seek/play, timers, appearance, and the final tab. */
    suspend fun runNavigation(
        request: DebugLaunchRequest,
        container: AppContainer,
        navController: NavHostController
    ) {
        request.textZoom?.let { container.readerPreferences.setTextZoom(it) }
        request.readerDark?.let { container.readerPreferences.setDarkMode(it) }
        request.readerSearch?.let { container.pendingReaderSearch = it }
        request.readerPage?.let { container.pendingReaderPage = it }

        var opened: Book? = null
        request.openTitle?.let { prefix ->
            val book = findByTitle(container, prefix) ?: run {
                android.util.Log.w(TAG, "openTitle: no book starts with \"$prefix\"")
                return@let
            }
            opened = book
            if (book.hasAudio) {
                openInPlayer(container, book, startMs = request.seekToSeconds?.let { it * 1000L }, play = request.play)
                navController.navigate(PlayerRoute.forBook(book.id)) { launchSingleTop = true }
            } else {
                navController.navigate(ReaderRoute.forBook(book.id)) { launchSingleTop = true }
            }
        }
        request.readTitle?.let { prefix ->
            val book = findByTitle(container, prefix) ?: return@let
            opened = book
            navController.navigate(ReaderRoute.forBook(book.id)) { launchSingleTop = true }
        }
        if (request.showReader) {
            opened?.takeIf { it.hasEpub }?.let {
                navController.navigate(ReaderRoute.forBook(it.id)) { launchSingleTop = true }
            }
        }
        if (request.showImages) {
            opened?.let { navController.navigate(ViewerRoute.forBook(it.id)) { launchSingleTop = true } }
        }
        request.armTimerMinutes?.let { minutes ->
            awaitController(container)
            container.sleepTimerController.start(SleepTimerConfig.TimeBased(minutes * 60_000L, fadeOutSeconds = 10))
            navController.navigate(TopLevelDestination.Settings.route) { launchSingleTop = true }
        }
        if (request.armChapterTimer) {
            awaitController(container)
            container.sleepTimerController.start(SleepTimerConfig.ChapterBased(1, fadeOutSeconds = 10))
            navController.navigate(TopLevelDestination.Settings.route) { launchSingleTop = true }
        }
        request.tab?.let { name ->
            val dest = when (name.lowercase()) {
                "library" -> TopLevelDestination.Library
                "player" -> TopLevelDestination.Player
                "images" -> TopLevelDestination.Viewer
                // "timer" still resolves, so existing scripts keep working after the merge.
                "timer", "settings" -> TopLevelDestination.Settings
                else -> null
            }
            dest?.let { navController.navigate(it.route) { launchSingleTop = true } }
        }
    }

    /**
     * Replaces the usage log with generated sessions so the stats chart has something to show.
     *
     * Deterministic (`Random(seed)` keyed off the day) so a rerun produces the same history and
     * screenshots stay comparable. Sessions are shaped like real use rather than uniform noise:
     * a few per day at plausible hours, of plausible lengths, mostly listening, and weighted so
     * the first books in the library dominate — a flat distribution would make every bar the same
     * height and hide whether the stack is actually working.
     */
    private suspend fun seedStats(container: AppContainer, days: Int) {
        val books = container.bookRepository.books().first()
        if (books.isEmpty()) {
            android.util.Log.w(TAG, "seedStats: no books in the library, nothing to attribute to")
            return
        }
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val sessions = mutableListOf<ReadLogEntry>()

        for (dayOffset in days downTo 0) {
            val date = today.minusDays(dayOffset.toLong())
            val random = kotlin.random.Random(date.toEpochDay())
            // Most days have some listening; a third are quiet, which gives the chart gaps.
            val count = when (random.nextInt(100)) {
                in 0..32 -> 0
                in 33..70 -> 1
                in 71..92 -> 2
                else -> 3
            }
            val startHours = listOf(8, 12, 18, 21).shuffled(random)
            repeat(count) { i ->
                // Books early in the library get picked more often, so totals differ visibly.
                val weighted = random.nextInt(books.size * (books.size + 1) / 2)
                var index = 0
                var acc = books.size
                while (acc <= weighted && index < books.lastIndex) { index++; acc += books.size - index }
                val book = books[index]

                val minutes = 8 + random.nextInt(68)
                val hour = startHours[i % startHours.size]
                val start = date.atTime(hour, random.nextInt(60))
                    .atZone(zone).toInstant().toEpochMilli()
                val kind = if (book.hasAudio && random.nextInt(100) < 78) ReadLogKind.Listen
                else ReadLogKind.Read
                sessions += ReadLogEntry(
                    id = "seed-${date}-$i",
                    bookId = book.id,
                    bookTitle = book.title,
                    kind = kind,
                    startedAt = start,
                    endedAt = start + minutes * 60_000L,
                    startPosition = 0,
                    endPosition = minutes * 60_000L
                )
            }
        }
        container.readLogRepository.replaceAll(sessions)
        android.util.Log.i(TAG, "seedStats: wrote ${sessions.size} sessions over $days days")
    }

    private suspend fun findByTitle(container: AppContainer, prefix: String): Book? {
        val wanted = prefix.lowercase()
        return container.bookRepository.books().first().firstOrNull { it.title.lowercase().startsWith(wanted) }
    }

    /** Loads [book] into the session (like tapping it), optionally at [startMs], optionally playing. */
    private suspend fun openInPlayer(container: AppContainer, book: Book, startMs: Long?, play: Boolean) {
        if (!book.hasAudio) return
        container.playerHolder.connect()
        if (awaitController(container) == null) return
        val position = startMs ?: (container.positionRepository.get(book.id) ?: 0L)
        container.playerHolder.loadBook(book, position, playWhenReady = play)
    }

    private suspend fun awaitController(container: AppContainer) =
        withTimeoutOrNull(CONTROLLER_TIMEOUT_MS) { container.playerHolder.controller.filterNotNull().first() }

    private const val TAG = "DebugLaunch"
    private const val DEFAULT_SEED_DAYS = 120
    /**
     * How long a hook waits for the media controller. Generous because scripted runs launch
     * back to back: the previous launch's playback service can still be tearing down, and a
     * hook that gives up here silently loads the book at position 0 instead of where it asked.
     */
    private const val CONTROLLER_TIMEOUT_MS = 20_000L
}
