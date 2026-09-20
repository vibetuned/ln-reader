package com.vibetuned.ln_reader.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibetuned.ln_reader.data.model.ReadLogEntry
import com.vibetuned.ln_reader.data.repo.ReadLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsUiState(
    val sessions: List<ReadLogEntry> = emptyList(),
    val total: Int = 0,
    val isLoadingPage: Boolean = false,
    val reachedEnd: Boolean = false
)

/**
 * The session history, a page at a time.
 *
 * History is unbounded — every play and every reading stretch adds a row — so the list never
 * loads it whole. [loadMore] appends the next page and the screen calls it as the last rows come
 * into view. Pages are addressed by offset rather than by a cursor because the log is append-only
 * and ordered by a fixed key, so a page can't shift under us mid-scroll.
 */
class SessionsViewModel(
    private val readLogRepository: ReadLogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(total = readLogRepository.count()) }
        }
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoadingPage || current.reachedEnd) return
        _state.update { it.copy(isLoadingPage = true) }
        viewModelScope.launch {
            val page = readLogRepository.page(PAGE_SIZE, current.sessions.size)
            _state.update {
                it.copy(
                    sessions = it.sessions + page,
                    isLoadingPage = false,
                    reachedEnd = page.size < PAGE_SIZE
                )
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 30

        fun factory(readLogRepository: ReadLogRepository) = viewModelFactory {
            initializer { SessionsViewModel(readLogRepository) }
        }
    }
}
