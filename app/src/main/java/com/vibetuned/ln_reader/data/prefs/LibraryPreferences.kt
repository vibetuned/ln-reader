package com.vibetuned.ln_reader.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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

/**
 * A single collection's manual-sort state: whether it's in manual mode and, if so, the explicit
 * book-id order the user arranged. Ids no longer in the collection are ignored; new books not yet
 * placed fall to the end.
 */
data class CollectionSort(
    val manual: Boolean = false,
    val order: List<String> = emptyList()
)

private const val ORDER_SEPARATOR = ","

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

    /** Per-collection manual-sort state (mode flag + arranged order). */
    fun collectionSort(collectionId: String): Flow<CollectionSort> =
        context.libraryDataStore.data.map { prefs ->
            CollectionSort(
                manual = prefs[manualKey(collectionId)] ?: false,
                order = prefs[orderKey(collectionId)]
                    ?.split(ORDER_SEPARATOR)
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()
            )
        }

    suspend fun setCollectionManual(collectionId: String, manual: Boolean) {
        context.libraryDataStore.edit { it[manualKey(collectionId)] = manual }
    }

    /** Persist the arranged order and switch the collection into manual mode. */
    suspend fun setCollectionOrder(collectionId: String, orderedBookIds: List<String>) {
        context.libraryDataStore.edit { prefs ->
            prefs[orderKey(collectionId)] = orderedBookIds.joinToString(ORDER_SEPARATOR)
            prefs[manualKey(collectionId)] = true
        }
    }

    private fun manualKey(collectionId: String) = booleanPreferencesKey("collection_manual_$collectionId")
    private fun orderKey(collectionId: String) = stringPreferencesKey("collection_order_$collectionId")

    companion object {
        private val KEY_FIELD = stringPreferencesKey("library_sort_field")
        private val KEY_DIRECTION = stringPreferencesKey("library_sort_direction")
    }
}
