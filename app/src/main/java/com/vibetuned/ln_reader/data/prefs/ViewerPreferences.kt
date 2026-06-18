package com.vibetuned.ln_reader.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Separate DataStore file from DownloadPreferences to avoid "multiple DataStores active for the
// same file" at runtime.
private val Context.viewerDataStore by preferencesDataStore(name = "ln_reader_viewer_prefs")

/**
 * Per-book image-viewer preferences. Right now there's exactly one knob: which background colour
 * to use when the user has manually picked one. `null` means "auto-detect from the image".
 */
class ViewerPreferences(private val context: Context) {

    /** "light" / "dark" / null for the given book. */
    fun backgroundFor(bookId: String): Flow<String?> =
        context.viewerDataStore.data.map { it[keyFor(bookId)] }

    suspend fun setBackgroundFor(bookId: String, choice: String?) {
        context.viewerDataStore.edit { prefs ->
            val key = keyFor(bookId)
            if (choice == null) prefs.remove(key) else prefs[key] = choice
        }
    }

    private fun keyFor(bookId: String) = stringPreferencesKey("viewer_bg_$bookId")

    companion object {
        const val CHOICE_LIGHT = "light"
        const val CHOICE_DARK = "dark"
    }
}
