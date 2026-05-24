package com.vibetuned.ln_reader.ui.player

import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.model.Chapter

data class PlayerUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val isControllerReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val currentChapterIndex: Int
        get() {
            if (chapters.isEmpty()) return -1
            // Largest index whose startMs <= position
            var idx = -1
            for (i in chapters.indices) {
                if (chapters[i].startMs <= positionMs) idx = i else break
            }
            return idx
        }

    val currentChapterTitle: String?
        get() = chapters.getOrNull(currentChapterIndex)?.title

    /** Start time (book-relative) of the current chapter, or 0 when there are no chapters. */
    val currentChapterStartMs: Long
        get() = chapters.getOrNull(currentChapterIndex)?.startMs ?: 0L

    /** Length of the current chapter, or the whole-book duration when chapters are absent. */
    val currentChapterDurationMs: Long
        get() {
            if (chapters.isEmpty()) return durationMs
            val idx = currentChapterIndex.coerceAtLeast(0)
            val start = chapters.getOrNull(idx)?.startMs ?: 0L
            val end = chapters.getOrNull(idx + 1)?.startMs ?: durationMs
            return (end - start).coerceAtLeast(0L)
        }

    /** Position within the current chapter, clamped to [0, chapterDuration]. */
    val positionInChapterMs: Long
        get() = (positionMs - currentChapterStartMs).coerceIn(0L, currentChapterDurationMs)
}
