package com.vibetuned.ln_reader.di

import android.content.Context
import com.vibetuned.ln_reader.data.db.LnReaderDatabase
import com.vibetuned.ln_reader.data.prefs.DownloadPreferences
import com.vibetuned.ln_reader.data.repo.BookRepository
import com.vibetuned.ln_reader.data.repo.PositionRepository
import com.vibetuned.ln_reader.m4b.M4bParser
import com.vibetuned.ln_reader.player.PlayerHolder
import com.vibetuned.ln_reader.player.SleepTimerController

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: LnReaderDatabase by lazy {
        LnReaderDatabase.build(appContext)
    }

    val parser: M4bParser by lazy { M4bParser() }

    val downloadPreferences: DownloadPreferences by lazy {
        DownloadPreferences(appContext)
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

    val playerHolder: PlayerHolder by lazy {
        PlayerHolder(appContext)
    }

    val sleepTimerController: SleepTimerController by lazy {
        SleepTimerController(appContext, playerHolder, bookRepository)
    }
}
