package com.vibetuned.ln_reader

import android.app.Application
import com.vibetuned.ln_reader.di.AppContainer

class LnReaderApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Off the main thread: an interrupted zip import can leave a gigabyte behind.
        Thread({ container.bookRepository.cleanUpTemporaryFiles() }, "temp-cleanup").start()
    }
}
