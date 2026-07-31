package com.vibetuned.ln_reader.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.vibetuned.ln_reader.data.repo.BookRepository
import com.vibetuned.ln_reader.player.PlayerHolder
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ViewerViewModel(
    private val playerHolder: PlayerHolder,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    /** True once opened for a specific book; such a viewer pins to it and won't follow the player. */
    private var explicit = false

    init {
        // When opened without an explicit bookId (the bottom-nav Images tab), follow whatever the
        // controller is playing — and keep following when the book changes (e.g. continuing a
        // collection) so we never strand the finished book's images.
        playerHolder.connect()
        viewModelScope.launch {
            playerHolder.controller.collectLatest { controller ->
                if (controller == null) return@collectLatest
                if (!explicit) adoptCurrent(controller)
                val listener = object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (!explicit) {
                            mediaItem?.mediaId?.takeIf { it.isNotEmpty() }?.let { adopt(it) }
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

    fun open(bookId: String) {
        explicit = true
        if (_state.value.book?.id == bookId) return
        load(bookId)
    }

    private fun adoptCurrent(controller: MediaController) {
        controller.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }?.let { adopt(it) }
    }

    private fun adopt(bookId: String) {
        if (_state.value.book?.id == bookId) return
        load(bookId)
    }

    private fun load(bookId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val detail = bookRepository.getDetail(bookId)
            if (detail == null) {
                _state.update { it.copy(isLoading = false, error = "Book not found") }
                return@launch
            }
            _state.update { current ->
                if (current.book?.id == bookId) current
                else current.copy(
                    book = detail.book,
                    images = detail.images,
                    isLoading = false,
                    error = null
                )
            }
        }
    }

    companion object {
        fun factory(
            playerHolder: PlayerHolder,
            bookRepository: BookRepository
        ) = viewModelFactory {
            initializer { ViewerViewModel(playerHolder, bookRepository) }
        }
    }
}
