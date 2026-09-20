package com.vibetuned.ln_reader

import com.vibetuned.ln_reader.data.model.ReadLogEntry
import com.vibetuned.ln_reader.data.model.ReadLogKind
import com.vibetuned.ln_reader.data.repo.ReadingStatsBuilder
import com.vibetuned.ln_reader.data.repo.StatsGranularity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Bucketing for the usage chart: calendar edges, clipping, ordering. */
class ReadingStatsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun session(
        bookId: String,
        from: Long,
        to: Long,
        title: String = bookId,
        kind: ReadLogKind = ReadLogKind.Listen
    ) = ReadLogEntry("s-$bookId-$from", bookId, title, kind, from, to, 0, 0)

    @Test
    fun dayBucketsCoverTheLastFourteenLocalDays() {
        val now = at(2026, 3, 20, 15)
        val stats = ReadingStatsBuilder.build(emptyList(), StatsGranularity.Day, now, zone)
        assertEquals(14, stats.buckets.size)
        // The last bucket is today, the first is 13 days back.
        assertEquals(at(2026, 3, 20), stats.buckets.last().startMs)
        assertEquals(at(2026, 3, 7), stats.buckets.first().startMs)
        assertTrue(stats.isEmpty)
    }

    @Test
    fun aSessionLandsInTheDayItHappened() {
        val now = at(2026, 3, 20, 23)
        val stats = ReadingStatsBuilder.build(
            listOf(session("alice", at(2026, 3, 18, 21, 0), at(2026, 3, 18, 21, 30))),
            StatsGranularity.Day, now, zone
        )
        val day18 = stats.buckets.first { it.startMs == at(2026, 3, 18) }
        assertEquals(30 * 60_000L, day18.byBook["alice"])
        assertEquals(30 * 60_000L, stats.totalMs)
        // Every other bucket stays empty rather than picking up a zero entry.
        assertTrue(stats.buckets.filter { it.startMs != at(2026, 3, 18) }.all { it.byBook.isEmpty() })
    }

    @Test
    fun aSessionAcrossMidnightIsSplitBetweenBothDays() {
        // 23:40 -> 00:20 is twenty minutes on each side, not forty on the day it started.
        val now = at(2026, 3, 20, 12)
        val stats = ReadingStatsBuilder.build(
            listOf(session("oz", at(2026, 3, 18, 23, 40), at(2026, 3, 19, 0, 20))),
            StatsGranularity.Day, now, zone
        )
        assertEquals(20 * 60_000L, stats.buckets.first { it.startMs == at(2026, 3, 18) }.byBook["oz"])
        assertEquals(20 * 60_000L, stats.buckets.first { it.startMs == at(2026, 3, 19) }.byBook["oz"])
        assertEquals(40 * 60_000L, stats.totalMs)
    }

    @Test
    fun timeOutsideTheWindowIsDropped() {
        val now = at(2026, 3, 20, 12)
        // Starts before the 14-day window opens and runs an hour into it; only the inside counts.
        val stats = ReadingStatsBuilder.build(
            listOf(session("old", at(2026, 3, 6, 23, 0), at(2026, 3, 7, 1, 0))),
            StatsGranularity.Day, now, zone
        )
        assertEquals(60 * 60_000L, stats.totalMs)
        assertEquals(60 * 60_000L, stats.buckets.first().byBook["old"])
    }

    @Test
    fun weeksStartOnMondayAndMonthsOnTheFirst() {
        val now = at(2026, 3, 20, 12) // a Friday
        val weeks = ReadingStatsBuilder.build(emptyList(), StatsGranularity.Week, now, zone)
        assertEquals(12, weeks.buckets.size)
        assertEquals(at(2026, 3, 16), weeks.buckets.last().startMs) // Monday of that week
        val months = ReadingStatsBuilder.build(emptyList(), StatsGranularity.Month, now, zone)
        assertEquals(12, months.buckets.size)
        assertEquals(at(2026, 3, 1), months.buckets.last().startMs)
        val years = ReadingStatsBuilder.build(emptyList(), StatsGranularity.Year, now, zone)
        assertEquals(5, years.buckets.size)
        assertEquals(at(2026, 1, 1), years.buckets.last().startMs)
    }

    @Test
    fun booksAreOrderedByTheirShareOfTheWindow() {
        val now = at(2026, 3, 20, 12)
        val stats = ReadingStatsBuilder.build(
            listOf(
                session("alice", at(2026, 3, 19, 10), at(2026, 3, 19, 10, 30), title = "Alice"),
                session("oz", at(2026, 3, 19, 12), at(2026, 3, 19, 14), title = "Oz"),
                session("alice", at(2026, 3, 20, 9), at(2026, 3, 20, 9, 15), title = "Alice"),
            ),
            StatsGranularity.Day, now, zone
        )
        assertEquals(listOf("oz", "alice"), stats.books.map { it.id })
        assertEquals(listOf("Oz", "Alice"), stats.books.map { it.title })
        assertEquals(120 * 60_000L, stats.books[0].totalMs)
        assertEquals(45 * 60_000L, stats.books[1].totalMs)
        // The tallest bar is the 19th: Oz's two hours plus Alice's half hour stacked on it.
        assertEquals(150 * 60_000L, stats.peakBucketMs)
    }

    @Test
    fun readingAndListeningBothCount() {
        val now = at(2026, 3, 20, 12)
        val stats = ReadingStatsBuilder.build(
            listOf(
                session("b", at(2026, 3, 20, 8), at(2026, 3, 20, 8, 20), kind = ReadLogKind.Listen),
                session("b", at(2026, 3, 20, 9), at(2026, 3, 20, 9, 10), kind = ReadLogKind.Read),
            ),
            StatsGranularity.Day, now, zone
        )
        assertEquals(30 * 60_000L, stats.totalMs)
    }

    @Test
    fun zeroLengthSessionsAreIgnored() {
        val now = at(2026, 3, 20, 12)
        val open = session("b", at(2026, 3, 20, 8), at(2026, 3, 20, 8))
        val stats = ReadingStatsBuilder.build(listOf(open), StatsGranularity.Day, now, zone)
        assertTrue(stats.isEmpty)
    }

    @Test
    fun windowStartMatchesTheFirstBucket() {
        val now = at(2026, 3, 20, 12)
        for (g in StatsGranularity.entries) {
            val stats = ReadingStatsBuilder.build(emptyList(), g, now, zone)
            assertEquals(stats.buckets.first().startMs, ReadingStatsBuilder.windowStartMs(g, now, zone))
        }
    }
}
