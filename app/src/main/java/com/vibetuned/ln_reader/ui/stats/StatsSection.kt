package com.vibetuned.ln_reader.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibetuned.ln_reader.data.repo.ReadingStats
import com.vibetuned.ln_reader.data.repo.StatsBucket
import com.vibetuned.ln_reader.data.repo.StatsGranularity
import com.vibetuned.ln_reader.ui.theme.AmberDeep
import com.vibetuned.ln_reader.ui.theme.ForestDeep
import com.vibetuned.ln_reader.ui.theme.ForestLight
import com.vibetuned.ln_reader.ui.theme.ForestPale
import com.vibetuned.ln_reader.ui.theme.SageLight
import com.vibetuned.ln_reader.ui.theme.SageSubtle

/**
 * Shades a stacked bar gets, in the order books are stacked (biggest share first).
 *
 * Steps through the palette from the deep binding green out to parchment, with the amber accent
 * last — adjacent books stay distinguishable without leaving the book's own colour world. A
 * library with more books than shades wraps, which is why the legend names them.
 */
private val BOOK_SHADES = listOf(ForestDeep, SageSubtle, ForestLight, SageLight, AmberDeep, ForestPale)

internal fun shadeFor(index: Int): Color = BOOK_SHADES[index % BOOK_SHADES.size]

/**
 * Time spent per book over a chosen window — the Settings screen's usage chart.
 *
 * Drawn from layout rather than a charting library: each bar is a Column of weighted segments,
 * which is all a stacked bar needs and keeps the app free of another dependency.
 */
@Composable
fun StatsSection(
    state: StatsUiState,
    onGranularityChange: (StatsGranularity) -> Unit,
    onOpenSessions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = state.stats
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsGranularity.entries.forEach { granularity ->
                FilterChip(
                    selected = state.granularity == granularity,
                    onClick = { onGranularityChange(granularity) },
                    label = { Text(granularityLabel(granularity)) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (stats == null) {
            Text(
                "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        if (stats.isEmpty) {
            Text(
                "Nothing here yet — time spent listening and reading will appear once you play a book.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Text(
            formatTotal(stats.totalMs),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "across ${stats.books.size} " + if (stats.books.size == 1) "book" else "books",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        BarChart(stats)

        Spacer(Modifier.height(16.dp))
        Legend(stats)

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenSessions, modifier = Modifier.fillMaxWidth()) {
            Text("Details")
        }
    }
}

@Composable
private fun BarChart(stats: ReadingStats) {
    val peak = stats.peakBucketMs.coerceAtLeast(1L)
    // Labels would collide at 14 bars, so only every other day is named.
    val labelEvery = if (stats.buckets.size > 8) 2 else 1
    val shadeOf = stats.books.mapIndexed { i, b -> b.id to shadeFor(i) }.toMap()
    val order = stats.books.map { it.id }

    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        stats.buckets.forEachIndexed { index, bucket ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Bar(bucket = bucket, peakMs = peak, order = order, shadeOf = shadeOf)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (index % labelEvery == 0) bucket.label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** One stacked bar. Heights are weights inside a fixed-height column, so the stack always sums. */
@Composable
private fun Bar(
    bucket: StatsBucket,
    peakMs: Long,
    order: List<String>,
    shadeOf: Map<String, Color>
) {
    val fraction = (bucket.totalMs.toFloat() / peakMs).coerceIn(0f, 1f)
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (bucket.totalMs == 0L) {
            // A visible baseline: an empty day should read as "nothing", not as missing.
            Box(
                Modifier.fillMaxWidth().height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((120.dp * fraction).coerceAtLeast(3.dp))
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            ) {
                // Smallest share on top so the largest book anchors the bar.
                order.reversed().forEach { bookId ->
                    val ms = bucket.byBook[bookId] ?: return@forEach
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(ms.toFloat())
                            .background(shadeOf[bookId] ?: MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend(stats: ReadingStats) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        stats.books.forEachIndexed { index, book ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(shadeFor(index))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    book.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTotal(book.totalMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun granularityLabel(granularity: StatsGranularity): String = when (granularity) {
    StatsGranularity.Day -> "Days"
    StatsGranularity.Week -> "Weeks"
    StatsGranularity.Month -> "Months"
    StatsGranularity.Year -> "Years"
}

/** "4h 12m" / "12m" / "—". Shared by the total line and the legend. */
internal fun formatTotal(ms: Long): String {
    if (ms <= 0) return "—"
    val minutes = ms / 60_000
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}
