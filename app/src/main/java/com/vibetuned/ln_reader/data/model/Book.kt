package com.vibetuned.ln_reader.data.model

data class Book(
    val id: String,
    val uri: String,
    val title: String,
    val author: String?,
    val album: String?,
    val durationMs: Long,
    val coverPath: String?,
    val importedAt: Long,
    val fileSize: Long,
    val syncKey: String? = null,
    val epubPath: String? = null,
    val syncPath: String? = null,
    val collectionId: String? = null,
    val mediaKind: MediaKind = MediaKind.Audio,
    /** Pages in an EPUB-only book (0 for audiobooks). See `BookEntity.spineCount`. */
    val spineCount: Int = 0
) {
    val hasEpub: Boolean get() = epubPath != null
    val hasSync: Boolean get() = syncPath != null
    /** False for an EPUB-only book: nothing to play, so it opens in the reader instead. */
    val hasAudio: Boolean get() = mediaKind == MediaKind.Audio
}

/** What a library entry is made of. See `BookEntity.mediaKind`. */
enum class MediaKind { Audio, Epub }

/** Where the reader left off — see `ReadingPositionEntity`. */
data class ReadingPosition(
    val bookId: String,
    val spineIndex: Int,
    val scrollFraction: Float,
    val spineCount: Int,
    val updatedAt: Long
) {
    /** 0..1 progress through the book by page, with the scroll within the current page. */
    val fraction: Float
        get() = if (spineCount <= 0) 0f
        else ((spineIndex + scrollFraction.coerceIn(0f, 1f)) / spineCount).coerceIn(0f, 1f)
}

/** Kind of a `read_log` session. */
enum class ReadLogKind { Listen, Read }

data class ReadLogEntry(
    val id: String,
    val bookId: String,
    val bookTitle: String,
    val kind: ReadLogKind,
    val startedAt: Long,
    val endedAt: Long,
    val startPosition: Long,
    val endPosition: Long
) {
    val durationMs: Long get() = (endedAt - startedAt).coerceAtLeast(0L)
}

/** A user-created folder of books, with a live count and a few contained covers for its tile art. */
data class Collection(
    val id: String,
    val name: String,
    val bookCount: Int,
    /** Cover paths of some contained books (newest first), for the collection tile. */
    val coverPaths: List<String> = emptyList()
)

data class Chapter(
    val id: Long,
    val bookId: String,
    val orderIndex: Int,
    val title: String,
    val startMs: Long
)

data class EmbeddedImage(
    val id: Long,
    val bookId: String,
    val orderIndex: Int,
    val mimeType: String,
    val cachePath: String
)

data class BookDetail(
    val book: Book,
    val chapters: List<Chapter>,
    val images: List<EmbeddedImage>
)
