package com.vibetuned.ln_reader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.prefs.LibraryPreferences
import com.vibetuned.ln_reader.data.prefs.LibrarySort
import com.vibetuned.ln_reader.data.prefs.SortDirection
import com.vibetuned.ln_reader.data.prefs.SortField
import com.vibetuned.ln_reader.data.repo.BookRepository
import com.vibetuned.ln_reader.data.repo.CollectionRepository
import com.vibetuned.ln_reader.data.repo.PositionRepository
import com.vibetuned.ln_reader.data.repo.orderCollectionBooks
import com.vibetuned.ln_reader.data.repo.sortBooksByField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the library grid. When [collectionId] is null it shows the top of the library (collections
 * + loose books); when set it shows the books inside that one collection.
 */
class LibraryViewModel(
    private val collectionId: String?,
    private val bookRepository: BookRepository,
    private val positionRepository: PositionRepository,
    private val collectionRepository: CollectionRepository,
    private val libraryPreferences: LibraryPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        // Mirror the persisted sort into UI state so the menu shows the current field + direction.
        viewModelScope.launch {
            libraryPreferences.sort.collect { sort ->
                _state.update { it.copy(sortField = sort.field, sortDirection = sort.direction) }
            }
        }
        if (collectionId == null) {
            viewModelScope.launch {
                combine(
                    collectionRepository.collections(),
                    bookRepository.topLevelBooks(),
                    positionRepository.observeAllPositions(),
                    libraryPreferences.sort
                ) { collections, books, positions, sort ->
                    buildList<LibraryEntry> {
                        collections.forEach { add(LibraryEntry.CollectionEntry(it)) }
                        sortBooksByField(books, sort).forEach {
                            add(LibraryEntry.BookEntry(it, progressFraction(positions[it.id], it.durationMs)))
                        }
                    }
                }.collect { entries ->
                    _state.update { it.copy(entries = entries, isLoading = false) }
                }
            }
        } else {
            viewModelScope.launch {
                combine(
                    bookRepository.booksInCollection(collectionId),
                    positionRepository.observeAllPositions(),
                    libraryPreferences.sort,
                    libraryPreferences.collectionSort(collectionId)
                ) { books, positions, sort, collectionSort ->
                    val ordered = orderCollectionBooks(books, sort, collectionSort)
                    val entries = ordered.map {
                        LibraryEntry.BookEntry(it, progressFraction(positions[it.id], it.durationMs))
                    }
                    entries to collectionSort.manual
                }.collect { (entries, manual) ->
                    _state.update { it.copy(entries = entries, isLoading = false, manualSort = manual) }
                }
            }
            viewModelScope.launch {
                collectionRepository.collection(collectionId).collect { collection ->
                    _state.update { it.copy(collectionName = collection?.name) }
                }
            }
        }
    }

    private fun progressFraction(positionMs: Long?, durationMs: Long): Float {
        if (positionMs == null || durationMs <= 0L) return 0f
        return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    fun setSort(field: SortField, direction: SortDirection) {
        viewModelScope.launch {
            libraryPreferences.setSort(field, direction)
            // Choosing a field sort exits manual mode for this collection.
            collectionId?.let { libraryPreferences.setCollectionManual(it, false) }
        }
    }

    /** Switch the open collection into manual mode; the screen then opens the reorder list. */
    fun enableManual() {
        val id = collectionId ?: return
        viewModelScope.launch { libraryPreferences.setCollectionManual(id, true) }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, importProgress = null, error = null) }
            val result = bookRepository.import(uri, collectionId = collectionId) { progress ->
                _state.update { it.copy(importProgress = progress) }
            }
            _state.update { current ->
                current.copy(
                    isImporting = false,
                    importProgress = null,
                    error = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                        ?: result.exceptionOrNull()?.javaClass?.simpleName?.let { "Import failed: $it" }
                )
            }
        }
    }

    fun createCollection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { collectionRepository.create(trimmed) }
    }

    /**
     * Delete the collection currently open. When [deleteBooks] is true its books are removed from
     * the library (files and cached data cleaned) first; otherwise they move back to the top level.
     * Runs on this VM's scope, which stays alive until the screen navigates away in response to
     * [LibraryUiState.collectionDeleted].
     */
    fun deleteCollection(deleteBooks: Boolean) {
        val id = collectionId ?: return
        viewModelScope.launch {
            if (deleteBooks) {
                collectionRepository.bookIdsIn(id).forEach { bookRepository.delete(it) }
            }
            collectionRepository.delete(id)
            _state.update { it.copy(collectionDeleted = true) }
        }
    }

    fun delete(bookId: String) {
        viewModelScope.launch {
            bookRepository.delete(bookId)
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        fun factory(
            collectionId: String?,
            bookRepository: BookRepository,
            positionRepository: PositionRepository,
            collectionRepository: CollectionRepository,
            libraryPreferences: LibraryPreferences
        ) = viewModelFactory {
            initializer {
                LibraryViewModel(
                    collectionId,
                    bookRepository,
                    positionRepository,
                    collectionRepository,
                    libraryPreferences
                )
            }
        }
    }
}
