package com.vibetuned.ln_reader.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ln_reader_prefs")

/**
 * Stores the user-selected destination for downloaded m4b copies. `null` means "use the
 * default internal app storage" — that's also the initial state.
 *
 * The value, when set, is a SAF tree URI captured via [Intent.ACTION_OPEN_DOCUMENT_TREE].
 */
class DownloadPreferences(private val context: Context) {

    val downloadFolderUri: Flow<String?> = context.dataStore.data.map { it[KEY_FOLDER] }

    suspend fun setDownloadFolderUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(KEY_FOLDER)
            else prefs[KEY_FOLDER] = uri
        }
    }

    companion object {
        private val KEY_FOLDER = stringPreferencesKey("download_folder_uri")
    }
}
