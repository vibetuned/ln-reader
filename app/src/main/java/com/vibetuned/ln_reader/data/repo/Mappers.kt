package com.vibetuned.ln_reader.data.repo

import com.vibetuned.ln_reader.data.db.BookEntity
import com.vibetuned.ln_reader.data.db.ChapterEntity
import com.vibetuned.ln_reader.data.db.EmbeddedImageEntity
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.model.Chapter
import com.vibetuned.ln_reader.data.model.EmbeddedImage

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
    syncKey = syncKey
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
