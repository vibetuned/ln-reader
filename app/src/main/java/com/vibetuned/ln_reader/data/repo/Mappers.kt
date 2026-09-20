package com.vibetuned.ln_reader.data.repo

import com.vibetuned.ln_reader.data.db.BookEntity
import com.vibetuned.ln_reader.data.db.ChapterEntity
import com.vibetuned.ln_reader.data.db.CollectionWithCount
import com.vibetuned.ln_reader.data.db.EmbeddedImageEntity
import com.vibetuned.ln_reader.data.db.MEDIA_KIND_EPUB
import com.vibetuned.ln_reader.data.db.READ_LOG_KIND_READ
import com.vibetuned.ln_reader.data.db.ReadLogEntity
import com.vibetuned.ln_reader.data.db.ReadingPositionEntity
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.model.Chapter
import com.vibetuned.ln_reader.data.model.Collection
import com.vibetuned.ln_reader.data.model.EmbeddedImage
import com.vibetuned.ln_reader.data.model.MediaKind
import com.vibetuned.ln_reader.data.model.ReadLogEntry
import com.vibetuned.ln_reader.data.model.ReadLogKind
import com.vibetuned.ln_reader.data.model.ReadingPosition

internal fun BookEntity.toDomain() = Book(
    id = id,
    uri = uri,
    title = title,
    author = author,
    album = album,
    durationMs = durationMs,
    coverPath = coverPath,
    importedAt = importedAt,
    fileSize = fileSize,
    syncKey = syncKey,
    epubPath = epubPath,
    syncPath = syncPath,
    collectionId = collectionId,
    mediaKind = if (mediaKind == MEDIA_KIND_EPUB) MediaKind.Epub else MediaKind.Audio,
    spineCount = spineCount
)

internal fun ReadingPositionEntity.toDomain() = ReadingPosition(
    bookId = bookId,
    spineIndex = spineIndex,
    scrollFraction = scrollFraction,
    spineCount = spineCount,
    updatedAt = updatedAt
)

internal fun ReadLogEntity.toDomain() = ReadLogEntry(
    id = id,
    bookId = bookId,
    bookTitle = bookTitle,
    kind = if (kind == READ_LOG_KIND_READ) ReadLogKind.Read else ReadLogKind.Listen,
    startedAt = startedAt,
    endedAt = endedAt,
    startPosition = startPosition,
    endPosition = endPosition
)

internal fun CollectionWithCount.toDomain() = Collection(
    id = id,
    name = name,
    bookCount = bookCount
)

internal fun ChapterEntity.toDomain() = Chapter(
    id = id,
    bookId = bookId,
    orderIndex = orderIndex,
    title = title,
    startMs = startMs
)

internal fun EmbeddedImageEntity.toDomain() = EmbeddedImage(
    id = id,
    bookId = bookId,
    orderIndex = orderIndex,
    mimeType = mimeType,
    cachePath = cachePath
)
