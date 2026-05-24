package com.vibetuned.ln_reader

import android.app.Application
import com.vibetuned.ln_reader.di.AppContainer

class LnReaderApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
