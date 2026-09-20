package com.vibetuned.ln_reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        PositionEntity::class,
        EmbeddedImageEntity::class,
        CollectionEntity::class,
        ReadingPositionEntity::class,
        ReadLogEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class LnReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun embeddedImageDao(): EmbeddedImageDao
    abstract fun positionDao(): PositionDao
    abstract fun collectionDao(): CollectionDao
    abstract fun readingPositionDao(): ReadingPositionDao
    abstract fun readLogDao(): ReadLogDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN syncKey TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN epubPath TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN syncPath TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS collections (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE books ADD COLUMN collectionId TEXT")
            }
        }

        /**
         * v6: EPUB-only books and the usage log.
         * - `books.mediaKind` distinguishes an EPUB-only book from an audiobook; existing rows are
         *   all audio, hence the default. `uri` stays NOT NULL (empty for EPUB-only) — flipping its
         *   nullability would need a table rebuild on store-installed databases.
         * - `reading_positions` is the reader's saved place per book (page + scroll), the EPUB
         *   counterpart of `positions`.
         * - `read_log` records listening / reading sessions for the usage time-series. No foreign
         *   key on purpose: history should survive the book being removed from the library.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE books ADD COLUMN mediaKind TEXT NOT NULL DEFAULT '$MEDIA_KIND_AUDIO'"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS reading_positions (" +
                        "bookId TEXT NOT NULL PRIMARY KEY, " +
                        "spineIndex INTEGER NOT NULL, " +
                        "scrollFraction REAL NOT NULL, " +
                        "spineCount INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS read_log (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "bookId TEXT NOT NULL, " +
                        "bookTitle TEXT NOT NULL, " +
                        "kind TEXT NOT NULL, " +
                        "startedAt INTEGER NOT NULL, " +
                        "endedAt INTEGER NOT NULL, " +
                        "startPosition INTEGER NOT NULL, " +
                        "endPosition INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_read_log_bookId ON read_log (bookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_read_log_startedAt ON read_log (startedAt)")
            }
        }

        /**
         * v7: `books.spineCount` — an EPUB-only book's page count, taken at import so the library
         * tile can read "6 / 45" without opening the EPUB. Audiobooks keep the default 0.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN spineCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): LnReaderDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LnReaderDatabase::class.java,
                "ln-reader.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
