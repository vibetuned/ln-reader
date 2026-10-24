package com.vibetuned.ln_reader.ui.player

import com.vibetuned.ln_reader.player.SleepTimerState

/** What an armed timer reads as on the player cover and in the mini-player. See [sleepTimerLabel]. */
internal fun SleepTimerState.label(): String = sleepTimerLabel(
    msUntilStop = msUntilStop,
    chaptersLeft = (this as? SleepTimerState.UntilChapterBoundary)?.chaptersRemaining
)

/**
 * "29:41 left" for a time-based timer; for a chapter-based one, which chapter it stops at:
 * "3:12 left · end of chapter" when the current chapter is the last, "12:30 left · 2 chapters"
 * otherwise. Hours appear only once there is at least one: "1:30:00 left".
 *
 * Mirrors `SleepTimerLabel.text` in the iOS app, so the two read the same.
 */
internal fun sleepTimerLabel(msUntilStop: Long, chaptersLeft: Int?): String {
    // Round up, like the Settings countdown: a timer with 0.4 s to go still reads 0:01.
    val total = (msUntilStop.coerceAtLeast(0L) + 999) / 1000
    val countdown = if (total >= 3600) {
        "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
    } else {
        "%d:%02d".format(total / 60, total % 60)
    }
    return when {
        chaptersLeft == null -> "$countdown left"
        chaptersLeft <= 1 -> "$countdown left · end of chapter"
        else -> "$countdown left · $chaptersLeft chapters"
    }
}
