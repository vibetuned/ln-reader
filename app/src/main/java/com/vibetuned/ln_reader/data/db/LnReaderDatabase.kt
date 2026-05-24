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
        EmbeddedImageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class LnReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun embeddedImageDao(): EmbeddedImageDao
    abstract fun positionDao(): PositionDao

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

        fun build(context: Context): LnReaderDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LnReaderDatabase::class.java,
                "ln-reader.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
