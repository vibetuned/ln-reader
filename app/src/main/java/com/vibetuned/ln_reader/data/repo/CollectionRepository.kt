package com.vibetuned.ln_reader.data.repo

import com.vibetuned.ln_reader.data.db.BookDao
import com.vibetuned.ln_reader.data.db.CollectionDao
import com.vibetuned.ln_reader.data.db.CollectionEntity
import com.vibetuned.ln_reader.data.model.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

private const val MAX_TILE_COVERS = 9

class CollectionRepository(
    private val collectionDao: CollectionDao,
    private val bookDao: BookDao
) {
    fun collections(): Flow<List<Collection>> =
        combine(
            collectionDao.observeAllWithCounts(),
            bookDao.observeCollectionCovers()
        ) { counts, coverRows ->
            val coversByCollection = coverRows.groupBy { it.collectionId }
            counts.map { row ->
                row.toDomain().copy(
                    coverPaths = coversByCollection[row.id]
                        ?.map { it.coverPath }
                        ?.take(MAX_TILE_COVERS)
                        ?: emptyList()
                )
            }
        }

    fun collection(id: String): Flow<Collection?> =
        collectionDao.observeByIdWithCount(id).map { it?.toDomain() }

    /** Create a collection and return its new id. */
    suspend fun create(name: String): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        collectionDao.upsert(
            CollectionEntity(id = id, name = name.trim(), createdAt = System.currentTimeMillis())
        )
        id
    }

    suspend fun addBook(bookId: String, collectionId: String) = withContext(Dispatchers.IO) {
        bookDao.setCollection(bookId, collectionId)
    }

    suspend fun removeBook(bookId: String) = withContext(Dispatchers.IO) {
        bookDao.setCollection(bookId, null)
    }

    suspend fun bookIdsIn(collectionId: String): List<String> = withContext(Dispatchers.IO) {
        bookDao.idsInCollection(collectionId)
    }

    /**
     * Delete the collection, moving any remaining books back to the top level first. Books
     * themselves aren't touched here — callers that want the books gone delete them (via
     * [BookRepository.delete]) before calling this.
     */
    suspend fun delete(collectionId: String) = withContext(Dispatchers.IO) {
        bookDao.clearCollection(collectionId)
        collectionDao.delete(collectionId)
    }
}
