package com.vibetuned.ln_reader.ui.reader

import com.vibetuned.ln_reader.companion.EpubSearchMatch

data class ReaderPage(
    val rootRelativePath: String,
    val url: String
)

/** The match the WebView should highlight and scroll to once its page is loaded. */
data class SearchTarget(
    val spineIndex: Int,
    /** 0-based index of the match within the page, in document order. */
    val occurrence: Int,
    /** JS-ready regex source for the submitted query (see [com.vibetuned.ln_reader.companion.EpubTextSearch.jsPattern]). */
    val jsPattern: String
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
    val isAudioPlaying: Boolean = false,
    /** Reader appearance (persisted app-wide). */
    val isDark: Boolean = false,
    val textZoom: Int = 100,
    /** Whole-book text search. The search bar replaces the top bar while active. */
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    /** null = nothing submitted yet; empty = the submitted query had no matches. */
    val searchResults: List<EpubSearchMatch>? = null,
    /** JS regex for the query the results belong to (the field may have been edited since). */
    val searchPatternJs: String? = null,
    /** True while the results list is shown over the page. */
    val showSearchResults: Boolean = false,
    /** Index into [searchResults] of the match being viewed, for prev/next stepping. */
    val searchSelection: Int? = null,
    val searchTarget: SearchTarget? = null
) {
    fun withSearchCleared() = copy(
        searchActive = false,
        searchQuery = "",
        isSearching = false,
        searchResults = null,
        searchPatternJs = null,
        showSearchResults = false,
        searchSelection = null,
        searchTarget = null
    )
}
