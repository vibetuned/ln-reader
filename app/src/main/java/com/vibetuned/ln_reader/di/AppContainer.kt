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
