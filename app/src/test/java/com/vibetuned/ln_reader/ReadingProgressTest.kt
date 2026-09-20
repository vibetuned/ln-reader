package com.vibetuned.ln_reader

import com.vibetuned.ln_reader.data.model.ReadLogEntry
import com.vibetuned.ln_reader.data.model.ReadLogKind
import com.vibetuned.ln_reader.data.model.ReadingPosition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic behind the EPUB progress bar and the usage log's durations. Both are pure, so
 * they're checked here rather than on a device; the DAO/migration side is exercised by the iOS
 * core suite's equivalent tests and by running the app.
 */
class ReadingProgressTest {

    private fun position(index: Int, fraction: Float, count: Int) =
        ReadingPosition(bookId = "b", spineIndex = index, scrollFraction = fraction, spineCount = count, updatedAt = 0L)

    @Test
    fun fractionCombinesPageAndScroll() {
        // Half-way down the second of two pages is three quarters of the book.
        assertEquals(0.75f, position(1, 0.5f, 2).fraction, 0.0001f)
        assertEquals(0f, position(0, 0f, 2).fraction, 0.0001f)
        assertEquals(0.5f, position(1, 0f, 2).fraction, 0.0001f)
    }

    @Test
    fun fractionIsZeroWithoutASpineCount() {
        // A position saved before the spine was known must not divide by zero.
        assertEquals(0f, position(3, 0.5f, 0).fraction, 0.0001f)
    }

    @Test
    fun fractionClampsOutOfRangeScroll() {
        // The WebView can report slightly outside 0..1 while rubber-banding.
        assertEquals(1f, position(1, 1.4f, 2).fraction, 0.0001f)
        assertEquals(0.5f, position(1, -0.3f, 2).fraction, 0.0001f)
    }

    @Test
    fun fractionNeverExceedsOneOnTheLastPage() {
        assertEquals(1f, position(2, 1f, 3).fraction, 0.0001f)
    }

    @Test
    fun logDurationIsTheSessionSpanAndNeverNegative() {
        fun entry(start: Long, end: Long) = ReadLogEntry(
            id = "s", bookId = "b", bookTitle = "T", kind = ReadLogKind.Read,
            startedAt = start, endedAt = end, startPosition = 0, endPosition = 0
        )
        assertEquals(60_000L, entry(1_000L, 61_000L).durationMs)
        // A row that was opened but never heartbeat has zero length, not a negative one.
        assertEquals(0L, entry(5_000L, 5_000L).durationMs)
        // Clock adjustments must not produce a negative duration in the time-series.
        assertEquals(0L, entry(10_000L, 9_000L).durationMs)
    }

    @Test
    fun fractionSurvivesAPositionSavedBeforeTheSpineWasKnown() {
        // reading_positions rows written by an older build carry spineCount 0; the library falls
        // back to the book's own count rather than dividing by zero.
        assertEquals(0f, position(2, 0.5f, 0).fraction, 0.0001f)
    }
}
