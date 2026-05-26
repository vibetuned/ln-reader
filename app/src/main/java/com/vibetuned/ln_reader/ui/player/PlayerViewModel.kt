package com.vibetuned.ln_reader.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.vibetuned.ln_reader.companion.SyncManifestParser
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.model.BookDetail
import com.vibetuned.ln_reader.data.repo.BookRepository
import com.vibetuned.ln_reader.data.repo.PositionRepository
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

class PlayerViewModel(
    private val playerHolder: PlayerHolder,
    private val bookRepository: BookRepository,
    private val positionRepository: PositionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** Book the user asked to play but the controller wasn't ready yet. */
    private var pendingLoad: Pair<Book, Long>? = null

    init {
        playerHolder.connect()
        viewModelScope.launch {
            playerHolder.controller.collectLatest { controller ->
                if (controller == null) {
                    _state.update { it.copy(isControllerReady = false) }
                    return@collectLatest
                }
                _state.update {
                    it.copy(
                        isControllerReady = true,
                        playbackSpeed = controller.playbackParameters.speed.takeIf { s -> s > 0f } ?: 1.0f
                    )
                }
                pendingLoad?.let { (book, pos) ->
                    playerHolder.loadBook(book, pos)
                    pendingLoad = null
                }
                // If the controller is already playing a book we don't yet know about,
                // adopt it so reopening the screen mid-playback shows the right state.
                if (_state.value.book == null) {
                    controller.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }
                        ?.let { adoptCurrentBook(it) }
                }
                val listener = makeListener(controller)
                controller.addListener(listener)
                syncFromController(controller)
                try {
                    while (true) {
                        delay(POLL_INTERVAL_MS)
                        syncPositionFromController(controller)
                    }
                } finally {
                    controller.removeListener(listener)
                }
            }
        }
    }

    fun open(bookId: String) {
        if (_state.value.book?.id == bookId) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val detail = bookRepository.getDetail(bookId)
            if (detail == null) {
                _state.update { it.copy(isLoading = false, error = "Book not found") }
                return@launch
            }
            val startPos = positionRepository.get(bookId) ?: 0L
            val markers = buildMarkers(detail)
            _state.update {
                it.copy(
                    book = detail.book,
                    chapters = detail.chapters,
                    images = detail.images,
                    imageMarkers = markers,
                    durationMs = detail.book.durationMs,
                    positionMs = startPos,
                    isLoading = false
                )
            }
            val controller = playerHolder.controller.value
            if (controller == null) {
                pendingLoad = detail.book to startPos
            } else if (controller.currentMediaItem?.mediaId != bookId) {
                playerHolder.loadBook(detail.book, startPos)
            }
        }
    }

    fun togglePlay() {
        val c = playerHolder.controller.value ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(ms: Long) {
        playerHolder.controller.value?.seekTo(ms)
    }

    fun skipForward() {
        val c = playerHolder.controller.value ?: return
        c.seekTo((c.currentPosition + SKIP_FORWARD_MS).coerceAtMost(c.duration.coerceAtLeast(0)))
    }

    fun skipBack() {
        val c = playerHolder.controller.value ?: return
        c.seekTo((c.currentPosition - SKIP_BACK_MS).coerceAtLeast(0))
    }

    fun nextChapter() {
        val chapters = _state.value.chapters
        if (chapters.isEmpty()) return
        val idx = _state.value.currentChapterIndex
        val nextStart = chapters.getOrNull(idx + 1)?.startMs ?: return
        seekTo(nextStart)
    }

    fun prevChapter() {
        val chapters = _state.value.chapters
        if (chapters.isEmpty()) return
        val pos = _state.value.positionMs
        val idx = _state.value.currentChapterIndex
        val current = chapters.getOrNull(idx) ?: return
        // If we're more than 3s into the current chapter, restart it. Otherwise go back one.
        val target = if (pos - current.startMs > 3_000L) current.startMs
        else chapters.getOrNull(idx - 1)?.startMs ?: 0L
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        val c = playerHolder.controller.value ?: return
        c.playbackParameters = PlaybackParameters(speed)
    }

    private fun makeListener(controller: MediaController) = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }
        override fun onPlaybackParametersChanged(params: PlaybackParameters) {
            _state.update { it.copy(playbackSpeed = params.speed) }
        }
        override fun onPlaybackStateChanged(state: Int) {
            // STATE_READY publishes the real duration; STATE_BUFFERING tells the UI to show a
            // spinner instead of the play icon (matters a lot when streaming from Drive).
            syncFromController(controller)
            _state.update { it.copy(isBuffering = state == Player.STATE_BUFFERING) }
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.takeIf { it.isNotEmpty() }
            if (id == null) {
                _state.update { it.copy(book = null, chapters = emptyList()) }
            } else if (id != _state.value.book?.id) {
                adoptCurrentBook(id)
            }
        }
    }

    /**
     * Mirror an already-loaded controller item into VM state without resetting playback.
     * Used when the player screen is opened while playback is already in progress.
     */
    private fun adoptCurrentBook(bookId: String) {
        viewModelScope.launch {
            val detail = bookRepository.getDetail(bookId) ?: return@launch
            val markers = buildMarkers(detail)
            _state.update { current ->
                // Bail if a concurrent open(...) already populated the book.
                if (current.book?.id == bookId) current
                else current.copy(
                    book = detail.book,
                    chapters = detail.chapters,
                    images = detail.images,
                    imageMarkers = markers,
                    durationMs = detail.book.durationMs.takeIf { it > 0 } ?: current.durationMs
                )
            }
        }
    }

    /**
     * Build scrubber markers from the attached sync manifest (if any), keeping only images that
     * map to an embedded m4b image by ordinal index.
     */
    private suspend fun buildMarkers(detail: BookDetail): List<ImageMarker> =
        withContext(Dispatchers.IO) {
            val syncPath = detail.book.syncPath ?: return@withContext emptyList()
            val manifest = SyncManifestParser.parse(File(syncPath)) ?: return@withContext emptyList()
            val byIndex = detail.images.associateBy { it.orderIndex }
            manifest.images.mapNotNull { img ->
                if (byIndex[img.ordinal] == null) return@mapNotNull null
                ImageMarker(
                    positionMs = (img.triggerSeconds * 1000).toLong(),
                    imageIndex = img.ordinal
                )
            }
        }

    private fun syncFromController(c: MediaController) {
        val dur = c.duration.takeIf { it > 0 } ?: _state.value.durationMs
        _state.update {
            it.copy(
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = dur,
                playbackSpeed = c.playbackParameters.speed.takeIf { s -> s > 0f } ?: 1.0f
            )
        }
    }

    private fun syncPositionFromController(c: MediaController) {
        _state.update {
            it.copy(
                positionMs = c.currentPosition.coerceAtLeast(0),
                isPlaying = c.isPlaying
            )
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 250L
        private const val SKIP_FORWARD_MS = 30_000L
        private const val SKIP_BACK_MS = 10_000L

        fun factory(
            playerHolder: PlayerHolder,
            bookRepository: BookRepository,
            positionRepository: PositionRepository
        ) = viewModelFactory {
            initializer {
                PlayerViewModel(playerHolder, bookRepository, positionRepository)
            }
        }
    }
}
