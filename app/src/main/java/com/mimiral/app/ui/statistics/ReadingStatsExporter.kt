package com.mimiral.app.ui.statistics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mimiral.app.data.local.entity.ReadingSessionEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports reading statistics to a CSV file, then shares it via Android intent.
 *
 * CSV format:
 *   date,book_id,start_time,end_time,duration_seconds,pages_read
 *
 * Also generates a summary section at the top of the file with totals.
 */
class ReadingStatsExporter(
    private val context: Context
) {
    /**
     * Export reading sessions to a .csv file in the app's cache directory.
     * Returns the File if successful, null if no sessions or on error.
     */
    suspend fun exportStatsToFile(
        sessions: List<ReadingSessionEntity>,
        totalBooksRead: Int,
        currentStreak: Int
    ): File? = withContext(Dispatchers.IO) {
        if (sessions.isEmpty()) return@withContext null

        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
        val timestamp = dateFormat.format(Date())
        val filename = "mimiral_statistics_$timestamp.csv"

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val file = File(exportDir, filename)

        try {
            val content = buildCsvContent(sessions, totalBooksRead, currentStreak)
            file.writeText(content)
            file
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Share an exported CSV file via Android SEND intent with FileProvider.
     */
    fun shareExportedFile(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Mimiral Reading Statistics")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share statistics via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Build the CSV content with a summary header and session rows.
     */
    private fun buildCsvContent(
        sessions: List<ReadingSessionEntity>,
        totalBooksRead: Int,
        currentStreak: Int
    ): String {
        val sb = StringBuilder()
        val exportDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        // ── Summary section ──────────────────────────────
        sb.appendLine("# Mimiral Reading Statistics Export")
        sb.appendLine("# Exported: $exportDate")
        sb.appendLine("# Total sessions: ${sessions.size}")
        sb.appendLine("# Total pages read: ${sessions.sumOf { it.pagesRead }}")
        sb.appendLine("# Total reading time (seconds): ${sessions.sumOf { it.durationSeconds }}")
        sb.appendLine("# Books completed: $totalBooksRead")
        sb.appendLine("# Current streak (days): $currentStreak")
        sb.appendLine()

        // ── CSV header ───────────────────────────────────
        sb.appendLine("date,book_id,start_time,end_time,duration_seconds,pages_read")

        // ── CSV data rows ───────────────────────────────
        // Sort by start_time ascending for chronological order
        val sortedSessions = sessions.sortedBy { it.startTime }
        for (session in sortedSessions) {
            val csvRow = "${session.date},${session.bookId}," +
                "${session.startTime},${session.endTime}," +
                "${session.durationSeconds},${session.pagesRead}"
            sb.appendLine(csvRow)
        }

        return sb.toString()
    }
}
