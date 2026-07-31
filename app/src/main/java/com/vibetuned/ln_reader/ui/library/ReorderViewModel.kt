package com.vibetuned.ln_reader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.prefs.LibraryPreferences
import com.vibetuned.ln_reader.data.repo.BookRepository
import com.vibetuned.ln_reader.data.repo.CollectionRepository
import com.vibetuned.ln_reader.data.repo.applyManualOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReorderUiState(
    val books: List<Book> = emptyList(),
    val collectionName: String? = null,
    val isLoading: Boolean = true,
    /** Flips true once the arranged order is persisted, so the screen can navigate back. */
    val saved: Boolean = false
)

/**
 * Loads a one-shot snapshot of a collection's books in their current manual order for the reorder
 * list to arrange, and persists the new order on save.
 */
class ReorderViewModel(
    private val collectionId: String,
    private val bookRepository: BookRepository,
    private val collectionRepository: CollectionRepository,
    private val libraryPreferences: LibraryPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(ReorderUiState())
    val state: StateFlow<ReorderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val books = bookRepository.booksInCollection(collectionId).first()
            val sort = libraryPreferences.collectionSort(collectionId).first()
            val name = collectionRepository.collection(collectionId).first()?.name
            _state.update {
                it.copy(
                    books = applyManualOrder(books, sort.order),
                    collectionName = name,
                    isLoading = false
                )
            }
        }
    }

    fun save(orderedBookIds: List<String>) {
        viewModelScope.launch {
            libraryPreferences.setCollectionOrder(collectionId, orderedBookIds)
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        fun factory(
            collectionId: String,
            bookRepository: BookRepository,
            collectionRepository: CollectionRepository,
            libraryPreferences: LibraryPreferences
        ) = viewModelFactory {
            initializer {
                ReorderViewModel(collectionId, bookRepository, collectionRepository, libraryPreferences)
            }
        }
    }
}
