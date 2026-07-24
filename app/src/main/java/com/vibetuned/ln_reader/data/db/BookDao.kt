package com.vibetuned.ln_reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    /** Books that sit at the top of the library (not inside any collection). */
    @Query("SELECT * FROM books WHERE collectionId IS NULL ORDER BY importedAt DESC")
    fun observeTopLevel(): Flow<List<BookEntity>>

    /** Books inside a given collection. */
    @Query("SELECT * FROM books WHERE collectionId = :collectionId ORDER BY importedAt DESC")
    fun observeByCollection(collectionId: String): Flow<List<BookEntity>>

    @Query("SELECT id FROM books WHERE collectionId = :collectionId")
    suspend fun idsInCollection(collectionId: String): List<String>

    /** Cover paths of all collected books (newest first), for building collection tile art. */
    @Query(
        "SELECT collectionId, coverPath FROM books " +
            "WHERE collectionId IS NOT NULL AND coverPath IS NOT NULL " +
            "ORDER BY importedAt DESC"
    )
    fun observeCollectionCovers(): Flow<List<CollectionCoverRow>>

    @Query("UPDATE books SET collectionId = :collectionId WHERE id = :id")
    suspend fun setCollection(id: String, collectionId: String?)

    /** Move every book out of a collection back to the top level. */
    @Query("UPDATE books SET collectionId = NULL WHERE collectionId = :collectionId")
    suspend fun clearCollection(collectionId: String)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE books SET epubPath = :path WHERE id = :id")
    suspend fun updateEpubPath(id: String, path: String?)

    @Query("UPDATE books SET syncPath = :path WHERE id = :id")
    suspend fun updateSyncPath(id: String, path: String?)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex ASC")
    fun observeForBook(bookId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex ASC")
    suspend fun forBook(bookId: String): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}

@Dao
interface EmbeddedImageDao {
    @Query("SELECT * FROM embedded_images WHERE bookId = :bookId ORDER BY orderIndex ASC")
    fun observeForBook(bookId: String): Flow<List<EmbeddedImageEntity>>

    @Query("SELECT * FROM embedded_images WHERE bookId = :bookId ORDER BY orderIndex ASC")
    suspend fun forBook(bookId: String): List<EmbeddedImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<EmbeddedImageEntity>)

    @Query("DELETE FROM embedded_images WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}

@Dao
interface PositionDao {
    @Query("SELECT positionMs FROM positions WHERE bookId = :bookId")
    fun observePositionMs(bookId: String): Flow<Long?>

    /** Every saved position. Used to show per-book progress bars across the library. */
    @Query("SELECT * FROM positions")
    fun observeAll(): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions WHERE bookId = :bookId")
    suspend fun get(bookId: String): PositionEntity?

    /**
     * Book id of the most recently saved position whose book still exists. Used on cold start to
     * reopen whatever the user was last listening to. The JOIN skips orphaned position rows that
     * may linger after a book is deleted.
     */
    @Query(
        "SELECT p.bookId FROM positions p " +
            "INNER JOIN books b ON b.id = p.bookId " +
            "ORDER BY p.updatedAt DESC LIMIT 1"
    )
    suspend fun mostRecentExistingBookId(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: PositionEntity)

    @Query("DELETE FROM positions WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}

/** Collection row plus a live count of the books currently inside it. */
data class CollectionWithCount(
    val id: String,
    val name: String,
    val createdAt: Long,
    val bookCount: Int
)

/** A single book's cover path tagged with the collection it belongs to (for collection tile art). */
data class CollectionCoverRow(
    val collectionId: String,
    val coverPath: String
)

@Dao
interface CollectionDao {
    @Query(
        "SELECT c.id AS id, c.name AS name, c.createdAt AS createdAt, " +
            "COUNT(b.id) AS bookCount FROM collections c " +
            "LEFT JOIN books b ON b.collectionId = c.id " +
            "GROUP BY c.id ORDER BY c.name COLLATE NOCASE"
    )
    fun observeAllWithCounts(): Flow<List<CollectionWithCount>>

    @Query(
        "SELECT c.id AS id, c.name AS name, c.createdAt AS createdAt, " +
            "COUNT(b.id) AS bookCount FROM collections c " +
            "LEFT JOIN books b ON b.collectionId = c.id " +
            "WHERE c.id = :id GROUP BY c.id"
    )
    fun observeByIdWithCount(id: String): Flow<CollectionWithCount?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: String)
}
