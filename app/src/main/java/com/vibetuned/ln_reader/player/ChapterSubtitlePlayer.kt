package com.vibetuned.ln_reader.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.vibetuned.ln_reader.data.model.Chapter

/**
 * Presents the current chapter as the session's subtitle: the line under the title in the media
 * notification, on the lock screen, in the system media controls and on car or Bluetooth displays.
 *
 * Those surfaces print the track's artist there, which for an audiobook means the author, and
 * almost no audiobook file carries an artist tag. With nothing to show, most of them leave the line
 * blank and some Pixel builds print the missing value as "null". The chapter is also simply the
 * more useful thing to show for a book listened to over many sittings.
 *
 * Only what the session *presents* changes. The media item itself is untouched, so nothing
 * re-buffers at a chapter boundary, the cast receiver still gets the book's own metadata, and the
 * in-app UI, which reads the item, is unaffected. Rewriting the item every chapter would also make
 * the player report a media-item transition each time, which the usage log reads as one listening
 * session ending and another starting.
 *
 * A book with no chapters keeps whatever artist it has.
 */
@OptIn(UnstableApi::class)
class ChapterSubtitlePlayer(wrapped: Player) : ForwardingSimpleBasePlayer(wrapped) {

    private var bookId: String? = null
    private var chapters: List<Chapter> = emptyList()

    /** The chapter the last published state named, so a tick that changes nothing stays quiet. */
    private var shownIndex = NONE

    /** Hands over [bookId]'s chapters; the subtitle follows them from the next state on. */
    fun setChapters(bookId: String, chapters: List<Chapter>) {
        this.bookId = bookId
        this.chapters = chapters.sortedBy { it.startMs }
        invalidateState()
    }

    /**
     * Re-checks which chapter is playing. Steady playback crossing a chapter boundary raises no
     * player event, so nothing else would notice. Costs one scan of the chapter list, and publishes
     * only when the chapter actually changed.
     */
    fun refreshChapter() {
        if (chapterIndex() != shownIndex) invalidateState()
    }

    override fun getState(): State {
        val state = super.getState()
        val index = chapterIndex()
        shownIndex = index
        if (index == NONE) return state

        val subtitle = chapterSubtitle(chapters, index)
        val metadata = state.currentMetadata.buildUpon()
            // Artist is what the system controls, the lock screen and car displays print under the
            // title; subtitle covers the surfaces that read the display fields instead.
            .setArtist(subtitle)
            .setSubtitle(subtitle)
            .build()
        // The same overload the base class builds its state with, so only the metadata differs.
        return state.buildUpon()
            .setPlaylist(state.timeline, state.currentTracks, metadata)
            .build()
    }

    private fun chapterIndex(): Int {
        if (player.currentMediaItem?.mediaId != bookId) return NONE
        return chapterIndexAt(chapters, player.currentPosition)
    }

    private companion object {
        const val NONE = -1
    }
}

/**
 * The chapter playing at [positionMs]: the largest index whose start is at or before it, which is
 * the player screen's rule too. [chapters] must be sorted by start. -1 when there are none.
 */
internal fun chapterIndexAt(chapters: List<Chapter>, positionMs: Long): Int {
    if (chapters.isEmpty()) return -1
    var index = 0
    for (i in chapters.indices) {
        if (chapters[i].startMs <= positionMs) index = i else break
    }
    return index
}

/**
 * "<title> · Chapter n of m", or just the position when the chapter has no title. Title first:
 * the long, descriptive chapter names are what a truncated line should keep.
 */
internal fun chapterSubtitle(chapters: List<Chapter>, index: Int): String {
    val position = "Chapter ${index + 1} of ${chapters.size}"
    val title = chapters[index].title.trim()
    return if (title.isEmpty()) position else "$title · $position"
}
