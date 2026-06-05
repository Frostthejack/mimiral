package com.mimiral.app.data.remote.opds

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.entity.BookEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages downloading books from OPDS acquisition links.
 *
 * Features:
 * - Downloads EPUB/PDF from OPDS acquisition links
 * - Saves to app-specific storage
 * - Adds downloaded book to local library via Room DB
 * - Shows download progress notification
 */
@Singleton
class OpdsDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OpdsHttpClient,
    private val bookDao: BookDao
) {

    companion object {
        private const val CHANNEL_ID = "opds_downloads"
        private const val NOTIFICATION_ID = 1001
        private val SUPPORTED_EXTENSIONS = setOf(
            "epub", "pdf", "mobi", "azw3", "fb2", "djvu",
            "cbz", "cbr", "txt", "rtf", "doc", "docx", "md"
        )
    }

    init {
        createNotificationChannel()
    }

    /**
     * Downloads a book from an OPDS acquisition link.
     */
    suspend fun downloadBook(
        entry: OpdsEntry,
        acquisitionLink: OpdsLink,
        catalogUsername: String? = null,
        catalogPassword: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val extension = getFileExtension(acquisitionLink)
            val fileName = "${sanitizeFileName(entry.title)}.$extension"
            val downloadDir = File(context.filesDir, "opds_downloads")
            downloadDir.mkdirs()
            val outputFile = File(downloadDir, fileName)

            showProgressNotification(entry.title, 0, 0)

            val result = httpClient.downloadFile(
                url = acquisitionLink.href,
                outputPath = outputFile.absolutePath,
                username = catalogUsername,
                password = catalogPassword,
                onProgress = { bytesDownloaded, totalBytes ->
                    val progress = if (totalBytes > 0) {
                        ((bytesDownloaded * 100) / totalBytes).toInt()
                    } else {
                        -1
                    }
                    showProgressNotification(entry.title, progress, bytesDownloaded)
                }
            )

            if (result.isFailure) {
                showErrorNotification(
                    entry.title,
                    result.exceptionOrNull()?.message ?: "Download failed"
                )
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            val bookEntity = BookEntity(
                filePath = outputFile.absolutePath,
                title = entry.title,
                author = entry.authors.firstOrNull()?.name,
                description = entry.summary?.ifBlank { entry.content },
                coverPath = null,
                format = extension.uppercase(),
                fileSize = outputFile.length(),
                dateAdded = System.currentTimeMillis(),
                isDownloaded = true,
                source = "OPDS",
                sourceId = entry.id
            )
            bookDao.insertBook(bookEntity)

            showCompleteNotification(entry.title)

            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            showErrorNotification(entry.title, e.message ?: "Download failed")
            Result.failure(e)
        }
    }

    private fun getFileExtension(link: OpdsLink): String {
        val href = link.href.substringBefore("?").lowercase()
        val fromHref = href.substringAfterLast('.', "")
        if (fromHref in SUPPORTED_EXTENSIONS) return fromHref

        val fromType = link.type?.lowercase() ?: ""
        return when {
            fromType.contains("epub") -> "epub"
            fromType.contains("pdf") -> "pdf"
            fromType.contains("mobi") -> "mobi"
            fromType.contains("x-mobipocket") -> "mobi"
            fromType.contains("vnd.amazon") -> "azw3"
            fromType.contains("fb2") -> "fb2"
            fromType.contains("djvu") -> "djvu"
            fromType.contains("cbz") -> "cbz"
            fromType.contains("cbr") -> "cbr"
            fromType.contains("plain") -> "txt"
            fromType.contains("rtf") -> "rtf"
            fromType.contains("msword") -> "doc"
            fromType.contains("wordprocessingml") -> "docx"
            fromType.contains("markdown") -> "md"
            else -> "epub"
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .take(100)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OPDS Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for OPDS book downloads"
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(title: String, progress: Int, bytesDownloaded: Long) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: $title")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        if (progress >= 0) {
            builder.setProgress(100, progress, false)
            builder.setContentText("$progress%")
        } else {
            builder.setContentText(formatBytes(bytesDownloaded))
            builder.setProgress(0, 0, true)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun showCompleteNotification(title: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun showErrorNotification(title: String, error: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText("$title: $error")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
