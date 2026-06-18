package com.vibetuned.ln_reader.data.repo

import com.vibetuned.ln_reader.data.db.PositionDao
import com.vibetuned.ln_reader.data.db.PositionEntity
import kotlinx.coroutines.flow.Flow

class PositionRepository(
    private val positionDao: PositionDao
) {
    fun observePositionMs(bookId: String): Flow<Long?> =
        positionDao.observePositionMs(bookId)

    suspend fun get(bookId: String): Long? =
        positionDao.get(bookId)?.positionMs

    /** Book the user most recently had playback in (and that still exists), or null. */
    suspend fun lastPlayedBookId(): String? =
        positionDao.mostRecentExistingBookId()

    suspend fun save(bookId: String, positionMs: Long) {
        positionDao.upsert(
            PositionEntity(
                bookId = bookId,
                positionMs = positionMs,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clear(bookId: String) {
        positionDao.delete(bookId)
    }
}
