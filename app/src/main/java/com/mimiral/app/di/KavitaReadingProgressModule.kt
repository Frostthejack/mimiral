package com.mimiral.app.di

/**
 * Hilt DI module for Kavita reading progress sync.
 *
 * All dependencies (KavitaApi, BookDao, ReadingProgressDao,
 * PendingOperationDao, ServerDao) are provided by other modules
 * or have @Inject constructors, so no explicit @Provides methods
 * are needed. KavitaReadingProgressRepository is @Singleton with
 * @Inject constructor and is resolved automatically by Hilt.
 */
// No explicit provides needed — Hilt resolves all dependencies via @Inject constructors.
