package com.vibetuned.ln_reader.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibetuned.ln_reader.player.SleepTimerConfig
import com.vibetuned.ln_reader.player.SleepTimerState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerControls(
    state: TimerUiState,
    onStartTime: (minutes: Int) -> Unit,
    onStartChapters: (count: Int) -> Unit,
    onStartEndOfChapter: () -> Unit,
    onCancel: () -> Unit,
    onPostpone: () -> Unit,
    onDismissExpired: () -> Unit,
    onFadeOutChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        state.timer?.let { active ->
            ActiveTimerCard(active, onCancel)
            Spacer(Modifier.height(24.dp))
        }

        state.expiredConfig?.let { cfg ->
            if (state.timer == null) {
                ExpiredTimerCard(
                    config = cfg,
                    onPostpone = onPostpone,
                    onDismiss = onDismissExpired
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        if (!state.isBookLoaded) {
            Text(
                "Open a book to use the sleep timer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Text(
            "Stop after time",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(5, 15, 30, 45, 60, 90).forEach { minutes ->
                SuggestionChip(
                    onClick = { onStartTime(minutes) },
                    label = { Text("$minutes min") }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Stop after chapters",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = onStartEndOfChapter,
                enabled = state.bookHasChapters,
                label = { Text("End of chapter") }
            )
            listOf(2, 3, 5).forEach { n ->
                SuggestionChip(
                    onClick = { onStartChapters(n) },
                    enabled = state.bookHasChapters,
                    label = { Text("+$n chapters") }
                )
            }
        }
        if (!state.bookHasChapters) {
            Spacer(Modifier.height(4.dp))
            Text(
                "This book has no chapter markers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = state.fadeOut,
                onCheckedChange = onFadeOutChanged
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Fade out audio", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Volume ramps to silence over the last 10 seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ExpiredTimerCard(
    config: SleepTimerConfig,
    onPostpone: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Sleep timer ended",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                describeConfigShort(config),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Shake the phone to keep listening, or tap Postpone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPostpone,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Postpone")
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

private fun describeConfigShort(config: SleepTimerConfig): String = when (config) {
    is SleepTimerConfig.TimeBased -> "Postpone for ${config.totalMs / 60_000} minutes"
    is SleepTimerConfig.ChapterBased -> {
        val n = config.chapterCount
        "Postpone for $n more " + if (n == 1) "chapter" else "chapters"
    }
    is SleepTimerConfig.EndOfChapter -> "Postpone to end of next chapter"
}

@Composable
private fun ActiveTimerCard(timer: SleepTimerState, onCancel: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Sleep timer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    formatTimerStatus(timer),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            FilledTonalButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

private fun formatTimerStatus(state: SleepTimerState): String {
    val totalSecs = (state.msUntilStop + 999) / 1000
    val minutes = totalSecs / 60
    val seconds = totalSecs % 60
    val countdown = "%d:%02d".format(minutes, seconds)
    return when (state) {
        is SleepTimerState.TimeRemaining -> countdown
        is SleepTimerState.UntilChapterBoundary ->
            if (state.chaptersRemaining <= 1) "$countdown left in chapter"
            else "${state.chaptersRemaining} chapters · $countdown"
    }
}
