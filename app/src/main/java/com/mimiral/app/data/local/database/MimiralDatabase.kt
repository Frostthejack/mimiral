package com.mimiral.app.data.local.database

import androidx.room.*
import com.mimiral.app.data.local.dao.*
import com.mimiral.app.data.local.entity.*

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
        OpdsCatalogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MimiralDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun collectionDao(): CollectionDao
    abstract fun serverDao(): ServerDao
    abstract fun opdsCatalogDao(): OpdsCatalogDao
}
