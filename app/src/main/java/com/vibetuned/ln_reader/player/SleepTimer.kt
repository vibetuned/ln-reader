package com.vibetuned.ln_reader.player

sealed interface SleepTimerConfig {
    val fadeOut: Boolean

    data class TimeBased(
        val totalMs: Long,
        override val fadeOut: Boolean = true
    ) : SleepTimerConfig

    data class ChapterBased(
        val chapterCount: Int,
        override val fadeOut: Boolean = true
    ) : SleepTimerConfig

    /** Convenience preset equivalent to [ChapterBased] with `chapterCount = 1`. */
    data class EndOfChapter(
        override val fadeOut: Boolean = true
    ) : SleepTimerConfig
}

sealed interface SleepTimerState {
    val msUntilStop: Long

    data class TimeRemaining(
        override val msUntilStop: Long,
        val totalMs: Long
    ) : SleepTimerState

    /** Active timer that stops at a chapter boundary. */
    data class UntilChapterBoundary(
        override val msUntilStop: Long,
        val chaptersRemaining: Int
    ) : SleepTimerState
}
