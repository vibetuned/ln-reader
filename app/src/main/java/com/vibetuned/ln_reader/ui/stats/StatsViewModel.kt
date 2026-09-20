package com.vibetuned.ln_reader.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibetuned.ln_reader.data.repo.ReadLogRepository
import com.vibetuned.ln_reader.data.repo.ReadingStats
import com.vibetuned.ln_reader.data.repo.StatsGranularity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsUiState(
    val granularity: StatsGranularity = StatsGranularity.Day,
    val stats: ReadingStats? = null,
    val isLoading: Boolean = true
)

/**
 * Backs the usage chart. Re-aggregates whenever the granularity changes, and again whenever the
 * screen comes back (`refresh`) — the log grows while you listen, and the chart is a snapshot
 * rather than a live query, which keeps it off the write path.
 */
class StatsViewModel(
    private val readLogRepository: ReadLogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init { refresh() }

    fun setGranularity(granularity: StatsGranularity) {
        if (granularity == _state.value.granularity) return
        _state.update { it.copy(granularity = granularity, isLoading = true) }
        refresh()
    }

    fun refresh() {
        val granularity = _state.value.granularity
        viewModelScope.launch {
            val stats = readLogRepository.stats(granularity)
            _state.update { it.copy(stats = stats, isLoading = false) }
        }
    }

    companion object {
        fun factory(readLogRepository: ReadLogRepository) = viewModelFactory {
            initializer { StatsViewModel(readLogRepository) }
        }
    }
}
