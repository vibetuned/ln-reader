package com.vibetuned.ln_reader

import com.vibetuned.ln_reader.data.model.Chapter
import com.vibetuned.ln_reader.player.chapterIndexAt
import com.vibetuned.ln_reader.player.chapterSubtitle
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterSubtitleTest {

    private fun chapters(vararg spec: Pair<String, Long>) =
        spec.mapIndexed { i, (title, start) -> Chapter(i.toLong(), "book", i, title, start) }

    // The Open Mind's first three chapters, starts in ms.
    private val openMind = chapters(
        "Author's Note" to 0L,
        "I. Atomic Explosives (May 1946)" to 76_024L,
        "II. Atomic Energy as a Contemporary Problem (September 1947)" to 2_199_145L
    )

    @Test
    fun `no chapters means no chapter`() {
        assertEquals(-1, chapterIndexAt(emptyList(), 5_000L))
    }

    @Test
    fun `position picks the chapter that has started`() {
        assertEquals(0, chapterIndexAt(openMind, 0L))
        assertEquals(1, chapterIndexAt(openMind, 2_190_000L))
        assertEquals(2, chapterIndexAt(openMind, 9_000_000L))
    }

    @Test
    fun `a chapter begins exactly at its start`() {
        assertEquals(1, chapterIndexAt(openMind, 2_199_144L))
        assertEquals(2, chapterIndexAt(openMind, 2_199_145L))
    }

    @Test
    fun `a position before the first start still names the first chapter`() {
        // Books whose first chapter starts after 0 (a short lead-in) shouldn't fall back to nothing.
        assertEquals(0, chapterIndexAt(chapters("One" to 3_000L, "Two" to 9_000L), 1_000L))
    }

    @Test
    fun `subtitle puts the title before the position`() {
        assertEquals(
            "I. Atomic Explosives (May 1946) · Chapter 2 of 3",
            chapterSubtitle(openMind, 1)
        )
    }

    @Test
    fun `untitled chapter shows only its position`() {
        val untitled = chapters("Prologue" to 0L, "   " to 1_000L)
        assertEquals("Chapter 2 of 2", chapterSubtitle(untitled, 1))
    }

    @Test
    fun `title whitespace is trimmed`() {
        assertEquals("X · Chapter 1 of 1", chapterSubtitle(chapters("  X \n" to 0L), 0))
    }
}
