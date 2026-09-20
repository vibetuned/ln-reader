package com.vibetuned.ln_reader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val title: String,
    val author: String?,
    val album: String?,
    val durationMs: Long,
    val coverPath: String?,
    val importedAt: Long,
    val fileSize: Long,
    /** Vestigial column from the removed sync feature. Always null on new imports. */
    val syncKey: String? = null,
    /**
     * True when [uri] points at a copy we created during import (in internal storage or in the
     * user-configured download folder). Tells [delete] whether it's safe to remove the file —
     * we mustn't delete the user's original source.
     */
    val isDownloaded: Boolean = false,
    /** Local path to an attached EPUB companion, or null. */
    val epubPath: String? = null,
    /** Local path to an attached sync_manifest.json companion, or null. */
    val syncPath: String? = null,
    /** Id of the collection this book lives in, or null when it sits at the top of the library. */
    val collectionId: String? = null,
    /**
     * What the book is made of: [MEDIA_KIND_AUDIO] for an imported m4b (the default, and every row
     * that predates the column), or [MEDIA_KIND_EPUB] for a book that is only an EPUB. An EPUB-only
     * book has no audio, so [uri] is empty and [durationMs] is 0 — read [MEDIA_KIND_EPUB] rather
     * than inferring from those. Kept as a flag on a NOT NULL [uri] instead of making [uri]
     * nullable: that would be a table-rebuild migration on a store-installed database, for no gain.
     */
    @ColumnInfo(defaultValue = MEDIA_KIND_AUDIO)
    val mediaKind: String = MEDIA_KIND_AUDIO,
    /**
     * Number of pages (EPUB spine documents) for an EPUB-only book, captured at import so the
     * library can show "6 / 45" before the book has ever been opened. 0 for audiobooks, and for
     * EPUB-only rows imported before the column existed — the library falls back to the count the
     * reader saved on `reading_positions` for those.
     */
    @ColumnInfo(defaultValue = "0")
    val spineCount: Int = 0
)

const val MEDIA_KIND_AUDIO = "audio"
const val MEDIA_KIND_EPUB = "epub"

/**
 * Where the reader left off in a book — the EPUB analog of [PositionEntity]. Audio books get their
 * place back from the saved playback position (the reader follows the narration); an EPUB-only book
 * has no narration to follow, so this is the only record of where the reader was: the spine page
 * and how far down it was scrolled. [spineCount] is stored alongside so the library can show
 * progress without opening the EPUB.
 */
@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val bookId: String,
    val spineIndex: Int,
    /** 0..1 scroll offset within the page. */
    val scrollFraction: Float,
    val spineCount: Int,
    val updatedAt: Long
)

/**
 * One session of using the app with a book: a stretch of listening or reading, for the usage
 * time-series. A row is opened when playback starts / the reader opens and closed when it stops;
 * while open, [endedAt] is advanced by a heartbeat so a killed process still leaves a truthful
 * row. Rows outlive their book on purpose — deleting a finished book shouldn't erase the hours
 * spent on it — which is why [bookTitle] is snapshotted and there is no foreign key.
 */
@Entity(
    tableName = "read_log",
    indices = [Index("bookId"), Index("startedAt")]
)
data class ReadLogEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val bookTitle: String,
    /** [READ_LOG_KIND_LISTEN] or [READ_LOG_KIND_READ]. */
    val kind: String,
    val startedAt: Long,
    val endedAt: Long,
    /** Playback ms for a listen session, spine index for a read session. */
    val startPosition: Long,
    val endPosition: Long
)

const val READ_LOG_KIND_LISTEN = "listen"
const val READ_LOG_KIND_READ = "read"

/** A user-created folder of books. Books reference it via [BookEntity.collectionId]; no nesting. */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "chapters",
    indices = [Index("bookId")]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val orderIndex: Int,
    val title: String,
    val startMs: Long
)

@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey val bookId: String,
    val positionMs: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "embedded_images",
    indices = [Index("bookId")]
)
data class EmbeddedImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val orderIndex: Int,
    val mimeType: String,
    val cachePath: String
)
