package com.vibetuned.ln_reader.di

import android.content.Context
import com.vibetuned.ln_reader.data.db.LnReaderDatabase
import com.vibetuned.ln_reader.data.prefs.DownloadPreferences
import com.vibetuned.ln_reader.data.prefs.LibraryPreferences
import com.vibetuned.ln_reader.data.prefs.ReaderPreferences
import com.vibetuned.ln_reader.data.prefs.ViewerPreferences
import com.vibetuned.ln_reader.data.repo.BookRepository
import com.vibetuned.ln_reader.data.repo.CollectionRepository
import com.vibetuned.ln_reader.data.repo.PositionRepository
import com.vibetuned.ln_reader.data.repo.ReadLogRepository
import com.vibetuned.ln_reader.data.repo.ReadingPositionRepository
import com.vibetuned.ln_reader.debug.DebugLaunchRequest
import com.vibetuned.ln_reader.m4b.M4bParser
import com.vibetuned.ln_reader.player.CollectionAdvanceController
import com.vibetuned.ln_reader.player.PlayerHolder
import com.vibetuned.ln_reader.player.SleepTimerController

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Whether this process has already auto-reopened the last-played book. Lives on the
     * process-scoped container so the restore fires once per fresh process — on a cold start and
     * after Android kills the backgrounded process — but not again across config changes (rotation)
     * within the same process. Read/written only from the main thread.
     */
    var lastBookRestoreHandled = false

    /**
     * Debug-build launch hooks parsed from the starting Intent (see `debug/DebugLaunch.kt`), waiting
     * for the UI to be up so they can import books and navigate. Consumed once by `MainActivity`.
     */
    var pendingDebugLaunch: DebugLaunchRequest? = null

    /**
     * Whether this process has already taken the launch Intent's debug hooks. Android re-delivers
     * the same Intent when the Activity is recreated — a rotation, or a `wm density` change during
     * a scripted capture — and without this guard `autoImport` would run again and duplicate every
     * book it had already added. Process-scoped for the same reason as [lastBookRestoreHandled].
     */
    var debugLaunchHandled = false

    /** Debug hook: a search to run as soon as the reader has a book loaded (`--es readerSearch q`). */
    var pendingReaderSearch: String? = null

    /** Debug hook: a 1-based page to turn to as soon as the reader has a book (`--ei readerPage n`). */
    var pendingReaderPage: Int? = null

    val database: LnReaderDatabase by lazy {
        LnReaderDatabase.build(appContext)
    }

    val parser: M4bParser by lazy { M4bParser() }

    val downloadPreferences: DownloadPreferences by lazy {
        DownloadPreferences(appContext)
    }

    val viewerPreferences: ViewerPreferences by lazy {
        ViewerPreferences(appContext)
    }

    val libraryPreferences: LibraryPreferences by lazy {
        LibraryPreferences(appContext)
    }

    val readerPreferences: ReaderPreferences by lazy {
        ReaderPreferences(appContext)
    }

    val bookRepository: BookRepository by lazy {
        BookRepository(
            context = appContext,
            database = database,
            bookDao = database.bookDao(),
            chapterDao = database.chapterDao(),
            imageDao = database.embeddedImageDao(),
            readingPositionDao = database.readingPositionDao(),
            parser = parser,
            downloadPreferences = downloadPreferences
        )
    }

    val positionRepository: PositionRepository by lazy {
        PositionRepository(database.positionDao())
    }

    val collectionRepository: CollectionRepository by lazy {
        CollectionRepository(database.collectionDao(), database.bookDao())
    }

    val readingPositionRepository: ReadingPositionRepository by lazy {
        ReadingPositionRepository(database.readingPositionDao())
    }

    val readLogRepository: ReadLogRepository by lazy {
        ReadLogRepository(database.readLogDao())
    }

    val playerHolder: PlayerHolder by lazy {
        PlayerHolder(appContext)
    }

    val sleepTimerController: SleepTimerController by lazy {
        SleepTimerController(appContext, playerHolder, bookRepository)
    }

    val collectionAdvanceController: CollectionAdvanceController by lazy {
        CollectionAdvanceController(playerHolder, bookRepository, positionRepository, libraryPreferences)
    }
}
