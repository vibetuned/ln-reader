package com.vibetuned.ln_reader.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.vibetuned.ln_reader.data.repo.BookRepository
import com.vibetuned.ln_reader.player.PlayerHolder
import com.vibetuned.ln_reader.player.SleepTimerConfig
import com.vibetuned.ln_reader.player.SleepTimerController
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimerViewModel(
    private val playerHolder: PlayerHolder,
    private val bookRepository: BookRepository,
    private val sleepTimer: SleepTimerController
) : ViewModel() {

    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state.asStateFlow()

    init {
        playerHolder.connect()
        viewModelScope.launch {
            sleepTimer.state.collect { ts ->
                _state.update { it.copy(timer = ts) }
            }
        }
        viewModelScope.launch {
            sleepTimer.expiredConfig.collect { cfg ->
                _state.update { it.copy(expiredConfig = cfg) }
            }
        }
        viewModelScope.launch {
            playerHolder.controller.collectLatest { controller ->
                if (controller == null) {
                    _state.update { it.copy(isBookLoaded = false, bookHasChapters = false) }
                    return@collectLatest
                }
                refreshBookState(controller.currentMediaItem?.mediaId)
                val listener = object : Player.Listener {
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        viewModelScope.launch { refreshBookState(item?.mediaId) }
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

    private suspend fun refreshBookState(mediaId: String?) {
        val id = mediaId?.takeIf { it.isNotEmpty() }
        if (id == null) {
            _state.update { it.copy(isBookLoaded = false, bookHasChapters = false) }
            return
        }
        val detail = bookRepository.getDetail(id)
        _state.update {
            it.copy(
                isBookLoaded = detail != null,
                bookHasChapters = (detail?.chapters?.size ?: 0) > 0
            )
        }
    }

    fun startTime(minutes: Int): Boolean =
        sleepTimer.start(SleepTimerConfig.TimeBased(minutes * 60_000L, _state.value.fadeOut))

    fun startChapters(count: Int): Boolean =
        sleepTimer.start(SleepTimerConfig.ChapterBased(count, _state.value.fadeOut))

    fun startEndOfChapter(): Boolean =
        sleepTimer.start(SleepTimerConfig.EndOfChapter(_state.value.fadeOut))

    fun cancel() = sleepTimer.cancel()

    fun postpone(): Boolean = sleepTimer.postpone()

    fun dismissExpired() = sleepTimer.dismissExpired()

    fun setFadeOut(enabled: Boolean) {
        _state.update { it.copy(fadeOut = enabled) }
    }

    companion object {
        fun factory(
            playerHolder: PlayerHolder,
            bookRepository: BookRepository,
            sleepTimer: SleepTimerController
        ) = viewModelFactory {
            initializer { TimerViewModel(playerHolder, bookRepository, sleepTimer) }
        }
    }
}
