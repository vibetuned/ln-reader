package com.vibetuned.ln_reader.data.db

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
    val syncPath: String? = null
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
