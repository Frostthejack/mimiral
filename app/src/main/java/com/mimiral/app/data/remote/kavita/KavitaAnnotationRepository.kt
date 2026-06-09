package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.mimiral.app.data.local.dao.ServerDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for Kavita Annotation operations.
 *
 * Orchestrates:
 * - Creating annotations (highlights + comments) on the Kavita server
 * - Fetching chapter and series annotations
 * - Updating annotation comments, spoiler status, and colors
 * - Deleting annotations
 * - Liking/unliking annotations (social feature)
 * - Exporting annotations
 */
@Singleton
class KavitaAnnotationRepository @Inject constructor(
    private val client: KavitaAnnotationClient,
    private val serverDao: ServerDao
) {
    companion object {
        private const val TAG = "KavitaAnnotationRepo"
    }

    /**
     * Initialize the Kavita client from the active server configuration.
     */
    private suspend fun initClient(): Boolean {
        val server = serverDao.getActiveServerByType("KAVITA") ?: run {
            Log.w(TAG, "No active Kavita server configured")
            return false
        }

        client.configure(
            url = server.url,
            key = server.apiKey,
            token = server.jwtToken,
            user = server.username,
            pass = server.password
        )
        return true
    }

    /**
     * Create a new annotation on the Kavita server.
     *
     * @param xPath DOM-based XPath locating the highlighted text (EPUB)
     * @param selectedText The highlighted text content
     * @param pageNumber Page number of the annotation
     * @param chapterId The Kavita chapter ID
     * @param color Optional highlight color (hex)
     * @param note Optional comment/note text
     * @param isSpoiler Whether the annotation is marked as spoiler
     * @return The created annotation, or null on failure
     */
    suspend fun createAnnotation(
        xPath: String? = null,
        selectedText: String,
        pageNumber: Int,
        chapterId: Int,
        color: String? = null,
        note: String? = null,
        isSpoiler: Boolean = false
    ): KavitaAnnotation? = withContext(Dispatchers.IO) {
        if (!initClient()) return@withContext null

        val request = KavitaAnnotationCreateRequest(
            xPath = xPath,
            selectedText = selectedText,
            pageNumber = pageNumber,
            chapterId = chapterId,
            color = color,
            note = note,
            isSpoiler = isSpoiler
        )

        when (val result = client.createAnnotation(request)) {
            is KavitaResult.Success -> {
                Log.d(TAG, "Created annotation: id=${result.data.id}")
                result.data
            }
            is KavitaResult.Error -> {
                Log.e(TAG, "Failed to create annotation: ${result.message}")
                null
            }
        }
    }

    /**
     * Update an existing annotation.
     *
     * @param annotationId The annotation ID to update
     * @param note Updated comment/note (null to keep existing)
     * @param isSpoiler Updated spoiler status (null to keep existing)
     * @param color Updated color (null to keep existing)
     * @return The updated annotation, or null on failure
     */
    suspend fun updateAnnotation(
        annotationId: Int,
        note: String? = null,
        isSpoiler: Boolean? = null,
        color: String? = null
    ): KavitaAnnotation? = withContext(Dispatchers.IO) {
        if (!initClient()) return@withContext null

        val request = KavitaAnnotationUpdateRequest(
            id = annotationId,
            note = note,
            isSpoiler = isSpoiler,
            color = color
        )

        when (val result = client.updateAnnotation(request)) {
            is KavitaResult.Success -> {
                Log.d(TAG, "Updated annotation: id=${result.data.id}")
                result.data
            }
            is KavitaResult.Error -> {
                Log.e(TAG, "Failed to update annotation: ${result.message}")
                null
            }
        }
    }

    /**
     * Delete an annotation from the Kavita server.
     *
     * @param annotationId The annotation ID to delete
     * @return True if deletion succeeded, false otherwise
     */
    suspend fun deleteAnnotation(annotationId: Int): Boolean =
        withContext(Dispatchers.IO) {
            if (!initClient()) return@withContext false

            when (val result = client.deleteAnnotation(annotationId)) {
                is KavitaResult.Success -> {
                    Log.d(TAG, "Deleted annotation: id=$annotationId")
                    true
                }
                is KavitaResult.Error -> {
                    Log.e(TAG, "Failed to delete annotation: ${result.message}")
                    false
                }
            }
        }

    /**
     * Get all annotations for a chapter.
     *
     * @param chapterId The Kavita chapter ID
     * @return List of annotations, or empty list on failure
     */
    suspend fun getChapterAnnotations(chapterId: Int): List<KavitaAnnotation> =
        withContext(Dispatchers.IO) {
            if (!initClient()) return@withContext emptyList()

            when (val result = client.getChapterAnnotations(chapterId)) {
                is KavitaResult.Success -> {
                    Log.d(TAG, "Fetched ${result.data.size} annotations for chapter $chapterId")
                    result.data
                }
                is KavitaResult.Error -> {
                    Log.e(TAG, "Failed to fetch chapter annotations: ${result.message}")
                    emptyList()
                }
            }
        }

    /**
     * Get all annotations for a series.
     *
     * @param seriesId The Kavita series ID
     * @return List of annotations, or empty list on failure
     */
    suspend fun getSeriesAnnotations(seriesId: Int): List<KavitaAnnotation> =
        withContext(Dispatchers.IO) {
            if (!initClient()) return@withContext emptyList()

            when (val result = client.getSeriesAnnotations(seriesId)) {
                is KavitaResult.Success -> {
                    Log.d(TAG, "Fetched ${result.data.size} annotations for series $seriesId")
                    result.data
                }
                is KavitaResult.Error -> {
                    Log.e(TAG, "Failed to fetch series annotations: ${result.message}")
                    emptyList()
                }
            }
        }

    /**
     * Like an annotation.
     *
     * @param annotationId The annotation ID to like
     * @return True if like succeeded, false otherwise
     */
    suspend fun likeAnnotation(annotationId: Int): Boolean =
        withContext(Dispatchers.IO) {
            if (!initClient()) return@withContext false

            when (val result = client.likeAnnotation(annotationId)) {
                is KavitaResult.Success -> true
                is KavitaResult.Error -> {
                    Log.e(TAG, "Failed to like annotation: ${result.message}")
                    false
                }
            }
        }

    /**
     * Unlike an annotation.
     *
     * @param annotationId The annotation ID to unlike
     * @return True if unlike succeeded, false otherwise
     */
    suspend fun unlikeAnnotation(annotationId: Int): Boolean =
        withContext(Dispatchers.IO) {
            if (!initClient()) return@withContext false

            when (val result = client.unlikeAnnotation(annotationId)) {
                is KavitaResult.Success -> true
                is KavitaResult.Error -> {
                    Log.e(TAG, "Failed to unlike annotation: ${result.message}")
                    false
                }
            }
        }

    /**
     * Export annotations for a chapter.
     *
     * @param chapterId The Kavita chapter ID
     * @return The export result, or null on failure
     */
    suspend fun exportAnnotations(chapterId: Int): KavitaAnnotationExport? =
        withContext(Dispatchers.IO) {
            if (!initClient()) return@withContext null

            when (val result = client.exportAnnotations(chapterId)) {
                is KavitaResult.Success -> {
                    Log.d(
                        TAG,
                        "Exported ${result.data.annotations.size} annotations " +
                            "for chapter $chapterId"
                    )
                    result.data
                }
                is KavitaResult.Error -> {
                    Log.e(TAG, "Failed to export annotations: ${result.message}")
                    null
                }
            }
        }
}
