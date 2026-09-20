package com.vibetuned.ln_reader.data.repo

import com.vibetuned.ln_reader.data.db.ReadingPositionDao
import com.vibetuned.ln_reader.data.db.ReadingPositionEntity
import com.vibetuned.ln_reader.data.model.ReadingPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Where the reader left off in each book, keyed by book — the EPUB counterpart of
 * [PositionRepository]. Saved by the reader as the user pages and scrolls; read back when a book
 * is reopened, and by the library to draw progress for books that have no audio to measure by.
 */
class ReadingPositionRepository(
    private val dao: ReadingPositionDao
) {
    fun observe(bookId: String): Flow<ReadingPosition?> =
        dao.observe(bookId).map { it?.toDomain() }

    /** bookId -> saved reading position, for every book that has one. */
    fun observeAll(): Flow<Map<String, ReadingPosition>> =
        dao.observeAll().map { rows -> rows.associate { it.bookId to it.toDomain() } }

    suspend fun get(bookId: String): ReadingPosition? = dao.get(bookId)?.toDomain()

    /** The most recent reading save (for a book that still exists), with its timestamp. */
    suspend fun lastRead(): LastActivity? =
        dao.mostRecentExisting()?.let { LastActivity(it.bookId, it.updatedAt) }

    suspend fun save(bookId: String, spineIndex: Int, scrollFraction: Float, spineCount: Int) {
        dao.upsert(
            ReadingPositionEntity(
                bookId = bookId,
                spineIndex = spineIndex.coerceAtLeast(0),
                scrollFraction = scrollFraction.coerceIn(0f, 1f),
                spineCount = spineCount.coerceAtLeast(0),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clear(bookId: String) = dao.delete(bookId)
}
