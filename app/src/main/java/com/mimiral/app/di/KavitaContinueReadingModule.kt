package com.mimiral.app.di

/**
 * Hilt DI module for Kavita Continue Reading, Next/Prev Chapter,
 * On Deck, and Time Left features.
 *
 * All dependencies (KavitaApi, ServerDao) are provided by other modules
 * or have @Inject constructors, so no explicit @Provides methods
 * are needed. KavitaContinueReadingRepository is @Singleton with
 * @Inject constructor and is resolved automatically by Hilt.
 */
// No explicit provides needed — Hilt resolves all dependencies via @Inject constructors.
