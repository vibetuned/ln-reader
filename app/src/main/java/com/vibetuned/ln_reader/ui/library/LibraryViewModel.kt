package com.vibetuned.ln_reader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibetuned.ln_reader.data.repo.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            bookRepository.books().collect { books ->
                _state.update { it.copy(books = books, isLoading = false) }
            }
        }
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
        fun factory(bookRepository: BookRepository) = viewModelFactory {
            initializer { LibraryViewModel(bookRepository) }
        }
    }
}
