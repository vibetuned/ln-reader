package com.vibetuned.ln_reader.ui.reader

data class ReaderPage(
    val rootRelativePath: String,
    val url: String
)

data class ReaderUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val bookTitle: String = "",
    val spine: List<ReaderPage> = emptyList(),
    val currentIndex: Int = 0,
    val activeBeatId: String? = null,
    val dataAttr: String = "data-beat-id",
    val hasSync: Boolean = false,
    val autoFollow: Boolean = true,
    /** True when the player is actively playing the book that's open in the reader. */
    val isAudioPlaying: Boolean = false
)
