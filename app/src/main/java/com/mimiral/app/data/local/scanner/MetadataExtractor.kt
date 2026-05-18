package com.mimiral.app.data.local.scanner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao
) {
    suspend fun extractAndUpdate(bookId: Long, pending: PendingBook) {
        // TODO: implement
    }
}
