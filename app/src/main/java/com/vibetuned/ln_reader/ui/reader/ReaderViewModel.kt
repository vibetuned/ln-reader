package com.vibetuned.ln_reader.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.session.MediaController
import com.vibetuned.ln_reader.companion.EpubReader
import com.vibetuned.ln_reader.companion.SyncManifest
import com.vibetuned.ln_reader.companion.SyncManifestParser
import com.vibetuned.ln_reader.data.repo.BookRepository
import com.vibetuned.ln_reader.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
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
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var bookId: String? = null
    private var manifest: SyncManifest? = null
    private var spinePaths: List<String> = emptyList()

    init {
        playerHolder.connect()
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
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val rootDir = File(appContext.filesDir, "epubs/$id")
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
            _state.update {
                it.copy(
                    isLoading = false,
                    bookTitle = detail.book.title,
                    spine = epub.spine.map { s -> ReaderPage(s.rootRelativePath, s.url) },
                    currentIndex = 0,
                    dataAttr = manifest?.dataAttr ?: "data-beat-id",
                    hasSync = manifest != null,
                    autoFollow = manifest != null
                )
            }
        }
    }

    fun nextPage() = _state.update {
        if (it.currentIndex < it.spine.lastIndex)
            it.copy(currentIndex = it.currentIndex + 1, autoFollow = false)
        else it
    }

    fun prevPage() = _state.update {
        if (it.currentIndex > 0)
            it.copy(currentIndex = it.currentIndex - 1, autoFollow = false)
        else it
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

    private fun spineIndexForXhtml(xhtml: String): Int {
        val exact = spinePaths.indexOfFirst { it == xhtml }
        if (exact >= 0) return exact
        val fileName = xhtml.substringAfterLast('/')
        return spinePaths.indexOfFirst { it.substringAfterLast('/') == fileName }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 400L

        fun factory(
            appContext: Context,
            playerHolder: PlayerHolder,
            bookRepository: BookRepository
        ) = viewModelFactory {
            initializer { ReaderViewModel(appContext, playerHolder, bookRepository) }
        }
    }
}
