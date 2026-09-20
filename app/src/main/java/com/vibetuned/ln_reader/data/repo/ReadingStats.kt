package com.vibetuned.ln_reader.data.repo

import com.vibetuned.ln_reader.data.model.ReadLogEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Bar width for the usage chart: what one bar covers. */
enum class StatsGranularity { Day, Week, Month, Year }

/** One book's share of the charted window, used to order the stack and pick its shade. */
data class StatsBook(val id: String, val title: String, val totalMs: Long)

/** One bar: a time bucket and how long each book was open inside it. */
data class StatsBucket(
    val startMs: Long,
    val label: String,
    /** bookId -> ms inside this bucket. Books with no time here are absent, not zero. */
    val byBook: Map<String, Long>
) {
    val totalMs: Long get() = byBook.values.sum()
}

/**
 * The charted window: consecutive buckets ending with the one containing "now", plus the books
 * that appear in it ordered by how much of it they account for.
 */
data class ReadingStats(
    val granularity: StatsGranularity,
    val buckets: List<StatsBucket>,
    val books: List<StatsBook>
) {
    val totalMs: Long get() = books.sumOf { it.totalMs }
    val isEmpty: Boolean get() = totalMs == 0L
    /** Longest single bar — the chart's vertical scale. */
    val peakBucketMs: Long get() = buckets.maxOfOrNull { it.totalMs } ?: 0L
}

/**
 * Turns raw `read_log` sessions into chart buckets.
 *
 * Pure and calendar-aware: bucket edges are real local day/week/month/year boundaries, not
 * fixed-width slices back from now, so "this week" means the week you'd name. A session is
 * **clipped** to each bucket it overlaps rather than counted wholly in the one it started in —
 * an hour of listening that runs through midnight is half an hour on each day, which is what a
 * time-spent chart has to say to add up.
 */
object ReadingStatsBuilder {

    fun bucketCount(granularity: StatsGranularity): Int = when (granularity) {
        StatsGranularity.Day -> 14
        StatsGranularity.Week -> 12
        StatsGranularity.Month -> 12
        StatsGranularity.Year -> 5
    }

    /** Start of the window that [build] charts — the oldest instant a session can contribute from. */
    fun windowStartMs(granularity: StatsGranularity, nowMs: Long, zone: ZoneId): Long =
        bucketStarts(granularity, nowMs, zone).first()

    fun build(
        entries: List<ReadLogEntry>,
        granularity: StatsGranularity,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): ReadingStats {
        val starts = bucketStarts(granularity, nowMs, zone)
        // One extra edge on the end so every bucket has an exclusive upper bound.
        val edges = starts + nextBucketStart(granularity, starts.last(), zone)
        val totals = List(starts.size) { mutableMapOf<String, Long>() }

        for (entry in entries) {
            val from = entry.startedAt
            val to = entry.endedAt
            if (to <= from) continue
            for (i in starts.indices) {
                val overlap = minOf(to, edges[i + 1]) - maxOf(from, edges[i])
                if (overlap > 0) {
                    totals[i][entry.bookId] = (totals[i][entry.bookId] ?: 0L) + overlap
                }
            }
        }

        val titles = entries.associate { it.bookId to it.bookTitle }
        val perBook = mutableMapOf<String, Long>()
        totals.forEach { bucket -> bucket.forEach { (id, ms) -> perBook[id] = (perBook[id] ?: 0L) + ms } }

        return ReadingStats(
            granularity = granularity,
            buckets = starts.mapIndexed { i, start ->
                StatsBucket(start, label(granularity, start, zone), totals[i].toMap())
            },
            // Biggest first: the stack reads largest-at-the-bottom and shades follow the same order.
            books = perBook.entries
                .sortedByDescending { it.value }
                .map { StatsBook(it.key, titles[it.key] ?: "", it.value) }
        )
    }

    private fun bucketStarts(granularity: StatsGranularity, nowMs: Long, zone: ZoneId): List<Long> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val count = bucketCount(granularity)
        val firstDays = when (granularity) {
            StatsGranularity.Day -> (count - 1 downTo 0).map { today.minusDays(it.toLong()) }
            StatsGranularity.Week -> {
                val thisWeek = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                (count - 1 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }
            }
            StatsGranularity.Month -> {
                val thisMonth = today.withDayOfMonth(1)
                (count - 1 downTo 0).map { thisMonth.minusMonths(it.toLong()) }
            }
            StatsGranularity.Year -> {
                val thisYear = today.withDayOfYear(1)
                (count - 1 downTo 0).map { thisYear.minusYears(it.toLong()) }
            }
        }
        return firstDays.map { it.atStartOfDay(zone).toInstant().toEpochMilli() }
    }

    private fun nextBucketStart(granularity: StatsGranularity, startMs: Long, zone: ZoneId): Long {
        val date = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
        val next = when (granularity) {
            StatsGranularity.Day -> date.plusDays(1)
            StatsGranularity.Week -> date.plusWeeks(1)
            StatsGranularity.Month -> date.plusMonths(1)
            StatsGranularity.Year -> date.plusYears(1)
        }
        return next.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun label(granularity: StatsGranularity, startMs: Long, zone: ZoneId): String {
        val date: LocalDate = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
        val pattern = when (granularity) {
            StatsGranularity.Day -> "d"
            StatsGranularity.Week -> "d MMM"
            StatsGranularity.Month -> "MMM"
            StatsGranularity.Year -> "yyyy"
        }
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    /** Whole days between two instants, for the "x days" summary line. */
    internal fun daysBetween(fromMs: Long, toMs: Long, zone: ZoneId): Long = ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(toMs).atZone(zone).toLocalDate()
    )
}
