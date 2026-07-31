package com.vibetuned.ln_reader.player

import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.vibetuned.ln_reader.data.prefs.LibraryPreferences
import com.vibetuned.ln_reader.data.repo.BookRepository
import com.vibetuned.ln_reader.data.repo.PositionRepository
import com.vibetuned.ln_reader.data.repo.orderCollectionBooks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A neighbouring book offered when the current one finishes inside a collection. */
data class CollectionEndBook(val id: String, val title: String, val coverPath: String?)

data class CollectionEndPrompt(
    val finishedTitle: String,
    val previous: CollectionEndBook?,
    val next: CollectionEndBook?
)

/**
 * Process-scoped watcher that reacts to a book reaching its end. If the finished book belongs to a
 * collection, it publishes a [CollectionEndPrompt] with the neighbouring books (in the collection's
 * displayed order) so the UI can offer to continue. It observes the shared [MediaController]
 * directly, so it fires no matter which screen is showing — including when listening via the
 * mini-player.
 */
class CollectionAdvanceController(
    private val playerHolder: PlayerHolder,
    private val bookRepository: BookRepository,
    private val positionRepository: PositionRepository,
    private val libraryPreferences: LibraryPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _prompt = MutableStateFlow<CollectionEndPrompt?>(null)
    val prompt: StateFlow<CollectionEndPrompt?> = _prompt.asStateFlow()

    init {
        scope.launch {
            playerHolder.controller.collectLatest { controller ->
                if (controller == null) return@collectLatest
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        // Only a natural end while playing counts — `playWhenReady` is still true
                        // then. This skips the paused restore-at-end on launch (a finished book
                        // whose saved position is its end), which would otherwise pop the prompt.
                        if (playbackState == Player.STATE_ENDED && controller.playWhenReady) {
                            onBookEnded(controller)
                        }
                    }
                }
                controller.addListener(listener)
                try {
                    awaitCancellation()
                } finally {
                    controller.removeListener(listener)
                }
            }
        }
    }

    private fun onBookEnded(controller: MediaController) {
        val bookId = controller.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
        scope.launch {
            val book = bookRepository.getDetail(bookId)?.book ?: return@launch
            val collectionId = book.collectionId ?: return@launch
            val books = bookRepository.booksInCollection(collectionId).first()
            val sort = libraryPreferences.sort.first()
            val collectionSort = libraryPreferences.collectionSort(collectionId).first()
            val ordered = orderCollectionBooks(books, sort, collectionSort)
            val index = ordered.indexOfFirst { it.id == bookId }
            if (index < 0) return@launch
            val previous = ordered.getOrNull(index - 1)
            val next = ordered.getOrNull(index + 1)
            if (previous == null && next == null) return@launch
            _prompt.value = CollectionEndPrompt(
                finishedTitle = book.title,
                previous = previous?.let { CollectionEndBook(it.id, it.title, it.coverPath) },
                next = next?.let { CollectionEndBook(it.id, it.title, it.coverPath) }
            )
        }
    }

    /** Load and play [bookId] (resuming any saved position), and clear the prompt. */
    fun continueTo(bookId: String) {
        scope.launch {
            val book = bookRepository.getDetail(bookId)?.book ?: return@launch
            val startPos = positionRepository.get(bookId) ?: 0L
            playerHolder.loadBook(book, startPos, playWhenReady = true)
            _prompt.value = null
        }
    }

    fun dismiss() {
        _prompt.value = null
    }
}
