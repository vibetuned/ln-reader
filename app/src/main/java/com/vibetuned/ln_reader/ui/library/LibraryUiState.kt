package com.vibetuned.ln_reader.ui.library

import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.repo.BookRepository

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    /** bookId -> playback progress through the book, 0f..1f. Missing entries mean 0. */
    val progress: Map<String, Float> = emptyMap(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val importProgress: BookRepository.ImportProgress? = null,
    val error: String? = null
)
