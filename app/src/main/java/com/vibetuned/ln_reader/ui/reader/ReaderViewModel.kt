package com.vibetuned.ln_reader.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.session.MediaController
import com.vibetuned.ln_reader.companion.EpubReader
import com.vibetuned.ln_reader.companion.EpubTextSearch
import com.vibetuned.ln_reader.companion.SyncManifest
import com.vibetuned.ln_reader.companion.SyncManifestParser
import com.vibetuned.ln_reader.data.model.ReadLogKind
import com.vibetuned.ln_reader.data.prefs.ReaderPreferences
import com.vibetuned.ln_reader.data.repo.BookRepository
import com.vibetuned.ln_reader.data.repo.ReadLogRepository
import com.vibetuned.ln_reader.data.repo.ReadingPositionRepository
import com.vibetuned.ln_reader.player.PlayerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReaderViewModel(
    private val appContext: Context,
    private val playerHolder: PlayerHolder,
    private val bookRepository: BookRepository,
    private val readerPreferences: ReaderPreferences,
    private val readingPositionRepository: ReadingPositionRepository,
    private val readLogRepository: ReadLogRepository,
    /** Debug hook (`--es readerSearch q`): a query to run as soon as the book is open. */
    private val debugSearchQuery: String? = null,
    /** Debug hook (`--ei readerPage n`): a 1-based page to turn to as soon as the book is open. */
    private val debugStartPage: Int? = null
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var bookId: String? = null
    private var manifest: SyncManifest? = null
    private var spinePaths: List<String> = emptyList()
    private var epubRootDir: File? = null

    /**
     * Writes that must outlive the screen: the final reading-position save and the read-log close
     * happen as the reader is torn down, after `viewModelScope` has been cancelled.
     */
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The open `read_log` read session (a Deferred id — the insert is asynchronous). */
    private var readSession: Deferred<String>? = null
    private var lastSavedIndex = -1
    private var lastSavedFraction = -1f

    init {
        playerHolder.connect()
        viewModelScope.launch {
            readerPreferences.darkMode.collect { dark -> _state.update { it.copy(isDark = dark) } }
        }
        viewModelScope.launch {
            readerPreferences.textZoom.collect { zoom -> _state.update { it.copy(textZoom = zoom) } }
        }
        viewModelScope.launch {
            playerHolder.controller.collectLatest { controller ->
                if (controller == null) {
                    _state.update { it.copy(isAudioPlaying = false) }
                    return@collectLatest
                }
                while (true) {
                    delay(POLL_INTERVAL_MS)
                    updateAudioPlaying(controller)
                    updateActiveBeat(controller)
                }
            }
        }
    }

    private fun updateAudioPlaying(controller: MediaController) {
        val id = bookId
        val playingThis = id != null &&
            controller.isPlaying &&
            controller.currentMediaItem?.mediaId == id
        if (playingThis != _state.value.isAudioPlaying) {
            _state.update { it.copy(isAudioPlaying = playingThis) }
        }
    }

    fun open(id: String) {
        if (bookId == id) return
        bookId = id
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val detail = bookRepository.getDetail(id)
            val epubPath = detail?.book?.epubPath
            if (detail == null || epubPath == null) {
                _state.update { it.copy(isLoading = false, error = "No EPUB attached to this book.") }
                return@launch
            }
            val rootDir = File(appContext.filesDir, "epubs/$id")
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    EpubReader.ensureExtracted(File(epubPath), rootDir)
                    EpubReader.parse(rootDir)
                }
            }
            val epub = result.getOrElse { t ->
                _state.update {
                    it.copy(isLoading = false, error = "Couldn't open EPUB: ${t.message ?: t.javaClass.simpleName}")
                }
                return@launch
            }
            manifest = detail.book.syncPath?.let {
                withContext(Dispatchers.IO) { SyncManifestParser.parse(File(it)) }
            }
            spinePaths = epub.spine.map { it.rootRelativePath }
            epubRootDir = rootDir

            // Where to open: when this book's narration is loaded in the player, the beat follow
            // takes over from page 0 as before. Otherwise — an EPUB-only book, or an audiobook
            // that isn't the one playing — come back to the saved page and scroll.
            val followingNarration = manifest != null &&
                playerHolder.controller.value?.currentMediaItem?.mediaId == id
            val saved = if (followingNarration) null else readingPositionRepository.get(id)
            val startIndex = (saved?.spineIndex ?: 0).coerceIn(0, (epub.spine.size - 1).coerceAtLeast(0))

            _state.update {
                it.withSearchCleared().copy(
                    isLoading = false,
                    bookTitle = detail.book.title,
                    spine = epub.spine.map { s -> ReaderPage(s.rootRelativePath, s.url) },
                    currentIndex = startIndex,
                    restoreScrollFraction = saved?.scrollFraction?.takeIf { f -> f > 0f },
                    dataAttr = manifest?.dataAttr ?: "data-beat-id",
                    hasSync = manifest != null,
                    autoFollow = manifest != null
                )
            }
            lastSavedIndex = saved?.spineIndex ?: -1
            lastSavedFraction = saved?.scrollFraction ?: -1f
            startReadSession(id, detail.book.title, startIndex)

            debugStartPage?.let { page ->
                val index = page - 1
                if (index in epub.spine.indices) {
                    _state.update { it.copy(currentIndex = index, autoFollow = false, restoreScrollFraction = null) }
                    persistPosition(index, 0f)
                }
            }
            debugSearchQuery?.takeIf { q -> q.isNotBlank() }?.let { q ->
                setSearchQuery(q)
                openSearch()
                submitSearch()
            }
        }
    }

    // ── reading position (the EPUB "page mark") ───────────────────────────────

    /**
     * The screen reports where the page is scrolled to, a few times a second while it's up. Saved
     * when it moved meaningfully, so an idle page costs nothing.
     */
    fun reportScroll(spineIndex: Int, fraction: Float) {
        if (spineIndex != _state.value.currentIndex) return
        if (_state.value.restoreScrollFraction != null) return // not yet restored; don't clobber it
        val clamped = fraction.coerceIn(0f, 1f)
        if (spineIndex == lastSavedIndex && kotlin.math.abs(clamped - lastSavedFraction) < 0.01f) return
        persistPosition(spineIndex, clamped)
    }

    /** The screen has applied [ReaderUiState.restoreScrollFraction]. */
    fun consumeRestoreScroll() = _state.update { it.copy(restoreScrollFraction = null) }

    private fun persistPosition(spineIndex: Int, fraction: Float) {
        val id = bookId ?: return
        val count = _state.value.spine.size
        lastSavedIndex = spineIndex
        lastSavedFraction = fraction
        persistScope.launch { readingPositionRepository.save(id, spineIndex, fraction, count) }
    }

    // ── read_log: read sessions ───────────────────────────────────────────────

    private fun startReadSession(id: String, title: String, startIndex: Int) {
        endReadSession()
        readSession = viewModelScope.async {
            readLogRepository.start(id, title, ReadLogKind.Read, startIndex.toLong())
        }
        viewModelScope.launch {
            while (true) {
                delay(READ_HEARTBEAT_MS)
                val session = readSession ?: break
                readLogRepository.heartbeat(session.await(), _state.value.currentIndex.toLong())
            }
        }
    }

    private fun endReadSession() {
        val session = readSession ?: return
        readSession = null
        val index = _state.value.currentIndex.toLong()
        persistScope.launch { readLogRepository.end(session.await(), index) }
    }

    override fun onCleared() {
        endReadSession()
        super.onCleared()
    }

    fun nextPage() {
        val cur = _state.value
        if (cur.currentIndex >= cur.spine.lastIndex) return
        _state.update { it.copy(currentIndex = it.currentIndex + 1, autoFollow = false, restoreScrollFraction = null) }
        persistPosition(cur.currentIndex + 1, 0f)
    }

    fun prevPage() {
        val cur = _state.value
        if (cur.currentIndex <= 0) return
        _state.update { it.copy(currentIndex = it.currentIndex - 1, autoFollow = false, restoreScrollFraction = null) }
        persistPosition(cur.currentIndex - 1, 0f)
    }

    fun setAutoFollow(enabled: Boolean) {
        _state.update { it.copy(autoFollow = enabled) }
        if (enabled) {
            // Jump straight to the page that holds the currently-highlighted beat.
            val beatId = _state.value.activeBeatId
            val beat = manifest?.beats?.firstOrNull { it.dataBeatId == beatId }
            if (beat != null) {
                val idx = spineIndexForXhtml(beat.xhtml)
                if (idx >= 0) _state.update { it.copy(currentIndex = idx) }
            }
        }
    }

    private fun updateActiveBeat(controller: MediaController) {
        val m = manifest ?: return
        val id = bookId ?: return
        // Only follow audio that belongs to the book we're reading.
        if (controller.currentMediaItem?.mediaId != id) return
        val beat = m.beatAt(controller.currentPosition) ?: return
        if (beat.dataBeatId == _state.value.activeBeatId) return
        val idx = spineIndexForXhtml(beat.xhtml)
        _state.update { cur ->
            cur.copy(
                activeBeatId = beat.dataBeatId,
                currentIndex = if (cur.autoFollow && idx >= 0) idx else cur.currentIndex
            )
        }
    }

    fun openSearch() = _state.update {
        it.copy(searchActive = true, showSearchResults = it.searchResults != null)
    }

    fun closeSearch() = _state.update { it.withSearchCleared() }

    fun setSearchQuery(query: String) = _state.update { it.copy(searchQuery = query) }

    fun hideSearchResults() = _state.update { it.copy(showSearchResults = false) }

    fun showSearchResults() = _state.update { it.copy(showSearchResults = true) }

    fun submitSearch() {
        val query = _state.value.searchQuery
        val rootDir = epubRootDir ?: return
        val paths = spinePaths
        val jsPattern = EpubTextSearch.jsPattern(query) ?: return
        if (paths.isEmpty()) return
        viewModelScope.launch {
            // Clearing the target also makes the WebView drop highlights from the previous query.
            _state.update {
                it.copy(
                    isSearching = true,
                    showSearchResults = true,
                    searchSelection = null,
                    searchTarget = null,
                    searchPatternJs = jsPattern
                )
            }
            val matches = withContext(Dispatchers.IO) {
                EpubTextSearch.search(rootDir, paths, query)
            }
            // The user may have edited the query and resubmitted while this scan ran.
            if (_state.value.searchQuery != query) return@launch
            _state.update { it.copy(isSearching = false, searchResults = matches) }
        }
    }

    fun openSearchResult(index: Int) {
        val current = _state.value
        val result = current.searchResults?.getOrNull(index) ?: return
        val pattern = current.searchPatternJs ?: return
        _state.update {
            it.copy(
                currentIndex = result.spineIndex,
                autoFollow = false,
                showSearchResults = false,
                searchSelection = index,
                searchTarget = SearchTarget(result.spineIndex, result.occurrence, pattern)
            )
        }
    }

    fun stepSearchResult(delta: Int) {
        val selection = _state.value.searchSelection ?: return
        openSearchResult(selection + delta)
    }

    fun toggleDarkMode() {
        viewModelScope.launch { readerPreferences.setDarkMode(!_state.value.isDark) }
    }

    fun zoomIn() {
        viewModelScope.launch {
            readerPreferences.setTextZoom(_state.value.textZoom + ReaderPreferences.ZOOM_STEP)
        }
    }

    fun zoomOut() {
        viewModelScope.launch {
            readerPreferences.setTextZoom(_state.value.textZoom - ReaderPreferences.ZOOM_STEP)
        }
    }

    private fun spineIndexForXhtml(xhtml: String): Int {
        val exact = spinePaths.indexOfFirst { it == xhtml }
        if (exact >= 0) return exact
        val fileName = xhtml.substringAfterLast('/')
        return spinePaths.indexOfFirst { it.substringAfterLast('/') == fileName }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 400L
        private const val READ_HEARTBEAT_MS = 10_000L

        fun factory(
            appContext: Context,
            playerHolder: PlayerHolder,
            bookRepository: BookRepository,
            readerPreferences: ReaderPreferences,
            readingPositionRepository: ReadingPositionRepository,
            readLogRepository: ReadLogRepository,
            debugSearchQuery: String? = null,
            debugStartPage: Int? = null
        ) = viewModelFactory {
            initializer {
                ReaderViewModel(
                    appContext, playerHolder, bookRepository, readerPreferences,
                    readingPositionRepository, readLogRepository, debugSearchQuery, debugStartPage
                )
            }
        }
    }
}
