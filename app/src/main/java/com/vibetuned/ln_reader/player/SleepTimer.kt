package com.vibetuned.ln_reader.player

sealed interface SleepTimerConfig {
    /** Length of the end-of-timer volume fade, in seconds. 0 means no fade. */
    val fadeOutSeconds: Int

    data class TimeBased(
        val totalMs: Long,
        override val fadeOutSeconds: Int = DEFAULT_FADE_OUT_SECONDS
    ) : SleepTimerConfig

    data class ChapterBased(
        val chapterCount: Int,
        override val fadeOutSeconds: Int = DEFAULT_FADE_OUT_SECONDS
    ) : SleepTimerConfig

    /** Convenience preset equivalent to [ChapterBased] with `chapterCount = 1`. */
    data class EndOfChapter(
        override val fadeOutSeconds: Int = DEFAULT_FADE_OUT_SECONDS
    ) : SleepTimerConfig

    companion object {
        const val DEFAULT_FADE_OUT_SECONDS = 10
    }
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
