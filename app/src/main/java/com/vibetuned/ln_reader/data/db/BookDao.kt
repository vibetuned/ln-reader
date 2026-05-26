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

    @Query("SELECT * FROM positions WHERE bookId = :bookId")
    suspend fun get(bookId: String): PositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: PositionEntity)

    @Query("DELETE FROM positions WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}
