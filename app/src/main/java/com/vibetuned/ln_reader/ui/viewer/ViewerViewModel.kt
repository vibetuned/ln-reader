package com.vibetuned.ln_reader.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibetuned.ln_reader.data.repo.BookRepository
import com.vibetuned.ln_reader.player.PlayerHolder
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

    init {
        // When opened without an explicit bookId, fall back to whatever the controller is
        // currently playing so the bottom-nav Images tab Just Works mid-listen.
        playerHolder.connect()
        viewModelScope.launch {
            playerHolder.controller.collectLatest { controller ->
                if (controller == null) return@collectLatest
                if (_state.value.book == null) {
                    controller.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }
                        ?.let { adopt(it) }
                }
            }
        }
    }

    fun open(bookId: String) {
        if (_state.value.book?.id == bookId) return
        load(bookId)
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
