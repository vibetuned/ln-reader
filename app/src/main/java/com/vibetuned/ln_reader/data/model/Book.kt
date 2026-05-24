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
    val syncKey: String? = null
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
