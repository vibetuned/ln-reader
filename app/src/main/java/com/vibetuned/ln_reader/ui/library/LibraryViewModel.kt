package com.vibetuned.ln_reader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibetuned.ln_reader.data.repo.BookRepository
import com.vibetuned.ln_reader.data.repo.PositionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val positionRepository: PositionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                bookRepository.books(),
                positionRepository.observeAllPositions()
            ) { books, positions ->
                books to books.associate { book ->
                    book.id to progressFraction(positions[book.id], book.durationMs)
                }
            }.collect { (books, progress) ->
                _state.update { it.copy(books = books, progress = progress, isLoading = false) }
            }
        }
    }

    private fun progressFraction(positionMs: Long?, durationMs: Long): Float {
        if (positionMs == null || durationMs <= 0L) return 0f
        return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, importProgress = null, error = null) }
            val result = bookRepository.import(uri) { progress ->
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
            bookRepository: BookRepository,
            positionRepository: PositionRepository
        ) = viewModelFactory {
            initializer { LibraryViewModel(bookRepository, positionRepository) }
        }
    }
}
