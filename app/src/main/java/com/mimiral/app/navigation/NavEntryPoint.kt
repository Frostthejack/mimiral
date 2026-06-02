package com.mimiral.app.navigation

import com.mimiral.app.data.local.dao.BookDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for accessing BookDao from non-Hilt classes (e.g., NavGraph).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavEntryPoint {
    fun bookDao(): BookDao
}
