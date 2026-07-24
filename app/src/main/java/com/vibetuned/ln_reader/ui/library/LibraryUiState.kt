package com.vibetuned.ln_reader.ui.library

import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.model.Collection
import com.vibetuned.ln_reader.data.prefs.SortDirection
import com.vibetuned.ln_reader.data.prefs.SortField
import com.vibetuned.ln_reader.data.repo.BookRepository

/** A tile in the library grid: either a collection folder or a book. */
sealed interface LibraryEntry {
    data class CollectionEntry(val collection: Collection) : LibraryEntry
    data class BookEntry(val book: Book, val progress: Float) : LibraryEntry
}

data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val sortField: SortField = SortField.DateAdded,
    val sortDirection: SortDirection = SortDirection.Desc,
    /** Non-null while viewing inside a collection; used as the screen title. */
    val collectionName: String? = null,
    /** Flips true once the open collection has been deleted, so the screen can navigate back. */
    val collectionDeleted: Boolean = false,
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val importProgress: BookRepository.ImportProgress? = null,
    val error: String? = null
)
