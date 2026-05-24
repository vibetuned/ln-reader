package com.vibetuned.ln_reader.ui.viewer

import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.model.EmbeddedImage

data class ViewerUiState(
    val book: Book? = null,
    val images: List<EmbeddedImage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
