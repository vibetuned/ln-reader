package com.vibetuned.ln_reader.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Its own DataStore file to avoid "multiple DataStores active for the same file" at runtime.
private val Context.readerDataStore by preferencesDataStore(name = "ln_reader_reader_prefs")

/** EPUB-reader appearance, remembered app-wide across books: dark mode and text zoom (percent). */
class ReaderPreferences(private val context: Context) {

    val darkMode: Flow<Boolean> = context.readerDataStore.data.map { it[KEY_DARK] ?: false }

    val textZoom: Flow<Int> = context.readerDataStore.data.map { it[KEY_ZOOM] ?: DEFAULT_TEXT_ZOOM }

    suspend fun setDarkMode(dark: Boolean) {
        context.readerDataStore.edit { it[KEY_DARK] = dark }
    }

    suspend fun setTextZoom(zoom: Int) {
        context.readerDataStore.edit { it[KEY_ZOOM] = zoom.coerceIn(MIN_TEXT_ZOOM, MAX_TEXT_ZOOM) }
    }

    companion object {
        const val DEFAULT_TEXT_ZOOM = 100
        const val MIN_TEXT_ZOOM = 80
        const val MAX_TEXT_ZOOM = 250
        const val ZOOM_STEP = 10
        private val KEY_DARK = booleanPreferencesKey("reader_dark_mode")
        private val KEY_ZOOM = intPreferencesKey("reader_text_zoom")
    }
}
