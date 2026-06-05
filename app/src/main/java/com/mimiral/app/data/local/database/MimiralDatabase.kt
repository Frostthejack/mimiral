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
import com.mimiral.app.data.local.dao.ReadingListDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.dao.ReadingSessionDao
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.dao.TagDao
import com.mimiral.app.data.local.entity.BookCollectionCrossRef
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.BookReadingListCrossRef
import com.mimiral.app.data.local.entity.BookTagCrossRef
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.local.entity.ChapterEntity
import com.mimiral.app.data.local.entity.ChapterFtsEntity
import com.mimiral.app.data.local.entity.CollectionEntity
import com.mimiral.app.data.local.entity.HighlightEntity
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import com.mimiral.app.data.local.entity.PdfSettingsEntity
import com.mimiral.app.data.local.entity.ReadingListEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.local.entity.ReadingSessionEntity
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
        ChapterFtsEntity::class,
        ReadingSessionEntity::class,
        ReadingListEntity::class,
        BookReadingListCrossRef::class
    ],
    version = 9,
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
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun readingListDao(): ReadingListDao
    abstract fun tagDao(): TagDao

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
                        "FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) " +
                        "ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_chapters_book_id` ON `chapters` (`book_id`)"
                )

                // Create FTS5 external content virtual table
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `chapters_fts` " +
                        "USING fts5(" +
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
                        "`rowid`, `book_id`, `chapter_index`, `title`, " +
                        "`content`) " +
                        "VALUES (new.`id`, new.`book_id`, " +
                        "new.`chapter_index`, new.`title`, new.`content`); " +
                        "END"
                )

                // Trigger: after delete on chapters, remove from FTS index
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `chapters_ad` " +
                        "AFTER DELETE ON `chapters` BEGIN " +
                        "INSERT INTO `chapters_fts`(`chapters_fts`, " +
                        "`rowid`, `book_id`, `chapter_index`, `title`, " +
                        "`content`) " +
                        "VALUES('delete', old.`id`, old.`book_id`, " +
                        "old.`chapter_index`, old.`title`, old.`content`); " +
                        "END"
                )

                // Trigger: after update on chapters, update FTS index
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `chapters_au` " +
                        "AFTER UPDATE ON `chapters` BEGIN " +
                        "INSERT INTO `chapters_fts`(`chapters_fts`, " +
                        "`rowid`, `book_id`, `chapter_index`, `title`, " +
                        "`content`) " +
                        "VALUES('delete', old.`id`, old.`book_id`, " +
                        "old.`chapter_index`, old.`title`, old.`content`); " +
                        "INSERT INTO `chapters_fts`(" +
                        "`rowid`, `book_id`, `chapter_index`, `title`, " +
                        "`content`) " +
                        "VALUES (new.`id`, new.`book_id`, " +
                        "new.`chapter_index`, new.`title`, new.`content`); " +
                        "END"
                )
            }
        }

        /**
         * Migration from v3 to v4:
         * - Add kavita_chapter_id column to bookmarks
         * - Add modified_time column to bookmarks
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `bookmarks` " +
                        "ADD COLUMN `kavita_chapter_id` INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE `bookmarks` " +
                        "ADD COLUMN `modified_time` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Migration from v4 to v5:
         * - Add kavita_series_id, kavita_library_id columns to books
         * - Add kavita_synced column to reading_progress
         * - Create servers table (Kavita/Calibre/Komga server configs)
         * - Create opds_catalogs table (OPDS feed subscriptions)
         * - Create pdf_settings table (per-book PDF crop settings)
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add Kavita sync columns to books table
                db.execSQL(
                    "ALTER TABLE `books` " +
                        "ADD COLUMN `kavita_series_id` INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE `books` " +
                        "ADD COLUMN `kavita_library_id` INTEGER"
                )

                // Add Kavita sync column to reading_progress table
                db.execSQL(
                    "ALTER TABLE `reading_progress` " +
                        "ADD COLUMN `kavita_synced` INTEGER NOT NULL DEFAULT 0"
                )

                // Create servers table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `servers` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`username` TEXT, " +
                        "`password` TEXT, " +
                        "`api_key` TEXT, " +
                        "`jwt_token` TEXT, " +
                        "`is_active` INTEGER NOT NULL, " +
                        "`last_sync_time` INTEGER)"
                )

                // Create opds_catalogs table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `opds_catalogs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`username` TEXT, " +
                        "`password` TEXT, " +
                        "`token` TEXT, " +
                        "`auth_type` TEXT NOT NULL, " +
                        "`is_active` INTEGER NOT NULL)"
                )

                // Create pdf_settings table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pdf_settings` (" +
                        "`book_id` INTEGER PRIMARY KEY NOT NULL, " +
                        "`crop_left` INTEGER NOT NULL, " +
                        "`crop_top` INTEGER NOT NULL, " +
                        "`crop_right` INTEGER NOT NULL, " +
                        "`crop_bottom` INTEGER NOT NULL, " +
                        "`auto_detected` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * Migration from v5 to v6:
         * - Create reading_sessions table for reading statistics tracking
         * - Add completed_at column to reading_progress
         * - Add times_completed column to reading_progress
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reading_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`book_id` INTEGER NOT NULL, " +
                        "`start_time` INTEGER NOT NULL, " +
                        "`end_time` INTEGER NOT NULL, " +
                        "`duration_seconds` INTEGER NOT NULL, " +
                        "`pages_read` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) " +
                        "ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_reading_sessions_book_id` ON `reading_sessions` (`book_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_reading_sessions_start_time` ON `reading_sessions` (`start_time`)"
                )
                db.execSQL(
                    "ALTER TABLE `reading_progress` " +
                        "ADD COLUMN `completed_at` INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE `reading_progress` " +
                        "ADD COLUMN `times_completed` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Migration from v6 to v7:
         * - Create reading_lists table (named reading lists: To Read, Reading, Finished, custom)
         * - Create book_reading_lists cross-reference table
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create reading_lists table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reading_lists` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`list_type` TEXT NOT NULL, " +
                        "`created_time` INTEGER NOT NULL, " +
                        "`sort_order` INTEGER NOT NULL)"
                )

                // Create book_reading_lists cross-reference table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `book_reading_lists` (" +
                        "`book_id` INTEGER NOT NULL, " +
                        "`reading_list_id` INTEGER NOT NULL, " +
                        "`added_time` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`book_id`, `reading_list_id`))"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_book_reading_lists_book_id` ON `book_reading_lists` (`book_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_book_reading_lists_reading_list_id` " +
                        "ON `book_reading_lists` (`reading_list_id`)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_book_reading_lists_book_id` ON `book_reading_lists` (`book_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_book_reading_lists_reading_list_id` " +
                        "ON `book_reading_lists` (`reading_list_id`)"
                )

                // Seed the three built-in system reading lists
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT OR IGNORE INTO `reading_lists`" +
                        "(`name`, `list_type`, `created_time`, `sort_order`) " +
                        "VALUES('To Read', 'TO_READ', $now, 0)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `reading_lists`" +
                        "(`name`, `list_type`, `created_time`, `sort_order`) " +
                        "VALUES('Reading', 'READING', $now, 1)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `reading_lists`" +
                        "(`name`, `list_type`, `created_time`, `sort_order`) " +
                        "VALUES('Finished', 'FINISHED', $now, 2)"
                )
            }
        }

        /**
         * Migration from v7 to v8:
         * - Add rating column to books table
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `books` " +
                        "ADD COLUMN `rating` INTEGER"
                )
            }
        }

        /**
         * Migration from v8 to v9:
         * - Add series_name, series_order columns to books
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `books` " +
                        "ADD COLUMN `series_name` TEXT"
                )
                db.execSQL(
                    "ALTER TABLE `books` " +
                        "ADD COLUMN `series_order` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
