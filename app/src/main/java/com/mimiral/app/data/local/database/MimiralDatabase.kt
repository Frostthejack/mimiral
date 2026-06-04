package com.mimiral.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.BookmarkDao
import com.mimiral.app.data.local.dao.ChapterDao
import com.mimiral.app.data.local.dao.CollectionDao
import com.mimiral.app.data.local.dao.HighlightDao
import com.mimiral.app.data.local.dao.OpdsCatalogDao
import com.mimiral.app.data.local.dao.PdfSettingsDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.entity.BookCollectionCrossRef
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.BookTagCrossRef
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.local.entity.ChapterEntity
import com.mimiral.app.data.local.entity.ChapterFtsEntity
import com.mimiral.app.data.local.entity.CollectionEntity
import com.mimiral.app.data.local.entity.HighlightEntity
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import com.mimiral.app.data.local.entity.PdfSettingsEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.local.entity.ServerEntity
import com.mimiral.app.data.local.entity.TagEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        CollectionEntity::class,
        BookCollectionCrossRef::class,
        TagEntity::class,
        BookTagCrossRef::class,
        ServerEntity::class,
        OpdsCatalogEntity::class,
        PdfSettingsEntity::class,
        ChapterEntity::class,
        ChapterFtsEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MimiralDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun collectionDao(): CollectionDao
    abstract fun serverDao(): ServerDao
    abstract fun opdsCatalogDao(): OpdsCatalogDao
    abstract fun pdfSettingsDao(): PdfSettingsDao
    abstract fun chapterDao(): ChapterDao

    companion object {
        /**
         * Migration from v1 to v2: initial schema (already applied).
         * Kept for completeness.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v1 -> v2 was the initial schema creation.
                // No-op if starting from v2.
            }
        }

        /**
         * Migration from v2 to v3:
         * - Create chapters table
         * - Create chapters_fts virtual table (FTS5 external content)
         * - Create triggers to keep FTS5 index in sync
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create chapters table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapters` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`book_id` INTEGER NOT NULL, " +
                        "`chapter_index` INTEGER NOT NULL, " +
                        "`title` TEXT, " +
                        "`content` TEXT NOT NULL, " +
                        "FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chapters_book_id` ON `chapters` (`book_id`)"
                )

                // Create FTS5 external content virtual table
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `chapters_fts` USING fts5(" +
                        "`book_id` UNINDEXED, " +
                        "`chapter_index` UNINDEXED, " +
                        "`title`, " +
                        "`content`, " +
                        "content=`chapters`, content_rowid=`id`)"
                )

                // Trigger: after insert on chapters, update FTS index
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `chapters_ai` " +
                        "AFTER INSERT ON `chapters` BEGIN " +
                        "INSERT INTO `chapters_fts`(" +
                        "`rowid`, `book_id`, `chapter_index`, `title`, `content`) " +
                        "VALUES (new.`id`, new.`book_id`, new.`chapter_index`, " +
                        "new.`title`, new.`content`); " +
                        "END"
                )

                // Trigger: after delete on chapters, remove from FTS index
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `chapters_ad` " +
                        "AFTER DELETE ON `chapters` BEGIN " +
                        "INSERT INTO `chapters_fts`(`chapters_fts`, " +
                        "`rowid`, `book_id`, `chapter_index`, `title`, `content`) " +
                        "VALUES('delete', old.`id`, old.`book_id`, " +
                        "old.`chapter_index`, old.`title`, old.`content`); " +
                        "END"
                )

                // Trigger: after update on chapters, update FTS index
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `chapters_au` " +
                        "AFTER UPDATE ON `chapters` BEGIN " +
                        "INSERT INTO `chapters_fts`(`chapters_fts`, " +
                        "`rowid`, `book_id`, `chapter_index`, `title`, `content`) " +
                        "VALUES('delete', old.`id`, old.`book_id`, " +
                        "old.`chapter_index`, old.`title`, old.`content`); " +
                        "INSERT INTO `chapters_fts`(" +
                        "`rowid`, `book_id`, `chapter_index`, `title`, `content`) " +
                        "VALUES (new.`id`, new.`book_id`, new.`chapter_index`, " +
                        "new.`title`, new.`content`); " +
                        "END"
                )
            }
        }

        /**
         * Migration from v3 to v4:
         * - Add kavita_synced column to reading_progress table
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `reading_progress` " +
                        "ADD COLUMN `kavita_synced` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
