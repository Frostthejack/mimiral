package com.mimiral.app.data.reader

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

/**
 * Resolves a file path or content URI to a usable File object.
 *
 * Handles three cases:
 * 1. Direct filesystem path (e.g., /storage/emulated/0/Download/book.epub)
 * 2. file:// URI (e.g., file:///storage/emulated/0/Download/book.epub)
 * 3. content:// URI (e.g., content://com.android.externalstorage.documents/tree/...)
 *
 * For content:// URIs, the file is copied to the app's cache directory
 * since Android's scoped storage prevents direct File access.
 *
 * Uses DocumentFile API as a fallback for SAF content URIs when
 * ContentResolver.openInputStream() fails due to permission issues.
 *
 * @param context Application context for ContentResolver access
 * @param filePath The file path, file:// URI, or content:// URI
 * @param cachePrefix Prefix for the cache file name
 * @return A File object that can be used for direct access, or null if resolution failed
 */
fun resolveFileToCache(
    context: Context,
    filePath: String,
    cachePrefix: String = "resolved"
): File? {
    // Case 1: Direct filesystem path
    val directFile = File(filePath)
    if (directFile.exists() && directFile.isFile) return directFile

    // Case 2 & 3: URI (file:// or content://)
    try {
        val uri = Uri.parse(filePath)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "content" && scheme != "file") {
            // Not a recognized URI scheme, try as file path with file:// prefix
            val fileUri = Uri.parse("file://$filePath")
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: return null
            val cacheFile = File(context.cacheDir, "${cachePrefix}_${filePath.hashCode().toString(16)}")
            FileOutputStream(cacheFile).use { out -> inputStream.copyTo(out) }
            inputStream.close()
            return cacheFile
        }

        // Try ContentResolver first (works for file:// and some content:// URIs)
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (_: SecurityException) {
            null
        }

        if (inputStream != null) {
            val cacheFile = File(context.cacheDir, "${cachePrefix}_${filePath.hashCode().toString(16)}")
            FileOutputStream(cacheFile).use { out -> inputStream.copyTo(out) }
            inputStream.close()
            return cacheFile
        }

        // Fallback: use DocumentFile API for SAF content URIs
        // This works when the app has persistable permission on the parent tree URI
        if (scheme == "content") {
            val docFile = DocumentFile.fromSingleUri(context, uri)
            if (docFile != null && docFile.exists() && docFile.isFile) {
                val fallbackStream = context.contentResolver.openInputStream(docFile.uri)
                if (fallbackStream != null) {
                    val cacheFile = File(context.cacheDir, "${cachePrefix}_${filePath.hashCode().toString(16)}")
                    FileOutputStream(cacheFile).use { out -> fallbackStream.copyTo(out) }
                    fallbackStream.close()
                    return cacheFile
                }
            }
        }

        return null
    } catch (e: Exception) {
        return null
    }
}
