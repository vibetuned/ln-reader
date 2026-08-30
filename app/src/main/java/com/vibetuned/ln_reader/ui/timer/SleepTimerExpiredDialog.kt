package com.vibetuned.ln_reader.ui.timer

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibetuned.ln_reader.player.SleepTimerConfig
import com.vibetuned.ln_reader.ui.common.appContainer

/**
 * Global host for the sleep-timer-expired prompt. Rendered once high in the tree so the dialog can
 * appear over any screen when the timer fires. Collection is lifecycle-aware, so the dialog only
 * shows while the app is in the foreground — in the background the notification posted by
 * [com.vibetuned.ln_reader.player.SleepTimerController] offers the same actions, and both surfaces
 * drive the same expired state, so acting on one clears the other.
 */
@Composable
fun SleepTimerExpiredHost() {
    val controller = appContainer().sleepTimerController
    val expired by controller.expiredConfig.collectAsStateWithLifecycle()
    expired?.let { config ->
        AlertDialog(
            onDismissRequest = { controller.dismissExpired() },
            title = { Text("Sleep timer ended") },
            text = { Text(describe(config)) },
            confirmButton = {
                TextButton(onClick = { controller.postpone() }) { Text("Postpone") }
            },
            dismissButton = {
                TextButton(onClick = { controller.dismissExpired() }) { Text("Dismiss") }
            }
        )
    }
}

private fun describe(config: SleepTimerConfig): String = when (config) {
    is SleepTimerConfig.TimeBased ->
        "Postpone to listen for another ${config.totalMs / 60_000} minutes, or shake the phone."
    is SleepTimerConfig.ChapterBased -> {
        val n = config.chapterCount
        val noun = if (n == 1) "chapter" else "chapters"
        "Postpone to listen for $n more $noun, or shake the phone."
    }
    is SleepTimerConfig.EndOfChapter ->
        "Postpone to keep going to the end of the next chapter, or shake the phone."
}
