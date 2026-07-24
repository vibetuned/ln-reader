package com.vibetuned.ln_reader.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Its own DataStore file, separate from the other prefs, to avoid "multiple DataStores active for
// the same file" at runtime.
private val Context.libraryDataStore by preferencesDataStore(name = "ln_reader_library_prefs")

/** Which field books are ordered by in the library / collection grids. */
enum class SortField { DateAdded, Name }

enum class SortDirection { Asc, Desc }

data class LibrarySort(
    val field: SortField = SortField.DateAdded,
    val direction: SortDirection = SortDirection.Desc
)

/** Persists the library's book sort (field + direction) across launches. */
class LibraryPreferences(private val context: Context) {

    val sort: Flow<LibrarySort> = context.libraryDataStore.data.map { prefs ->
        val field = prefs[KEY_FIELD]
            ?.let { runCatching { SortField.valueOf(it) }.getOrNull() }
            ?: SortField.DateAdded
        val direction = prefs[KEY_DIRECTION]
            ?.let { runCatching { SortDirection.valueOf(it) }.getOrNull() }
            ?: SortDirection.Desc
        LibrarySort(field, direction)
    }

    suspend fun setSort(field: SortField, direction: SortDirection) {
        context.libraryDataStore.edit { prefs ->
            prefs[KEY_FIELD] = field.name
            prefs[KEY_DIRECTION] = direction.name
        }
    }

    companion object {
        private val KEY_FIELD = stringPreferencesKey("library_sort_field")
        private val KEY_DIRECTION = stringPreferencesKey("library_sort_direction")
    }
}
