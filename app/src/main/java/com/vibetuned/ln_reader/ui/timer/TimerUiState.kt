package com.vibetuned.ln_reader.ui.timer

import com.vibetuned.ln_reader.player.SleepTimerConfig
import com.vibetuned.ln_reader.player.SleepTimerState

data class TimerUiState(
    val timer: SleepTimerState? = null,
    val expiredConfig: SleepTimerConfig? = null,
    val isBookLoaded: Boolean = false,
    val bookHasChapters: Boolean = false,
    val fadeOut: Boolean = true
)
