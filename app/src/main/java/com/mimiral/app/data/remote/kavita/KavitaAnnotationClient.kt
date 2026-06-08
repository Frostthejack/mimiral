package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * HTTP client for Kavita Annotation API operations.
 *
 * Handles:
 * - POST /api/Annotation/create — create a new annotation
 * - POST /api/Annotation/update — update an annotation (comment, spoiler, color)
 * - POST /api/Annotation/delete — delete an annotation
 * - GET  /api/Annotation/all?chapterId=X — get all annotations for a chapter
 * - GET  /api/Annotation/all-for-series?seriesId=X — get all annotations for a series
 * - POST /api/Annotation/like — like an annotation
 * - POST /api/Annotation/unlike — unlike an annotation
 * - GET  /api/Annotation/export?chapterId=X — export annotations for a chapter
 * - Authentication via API key or JWT token
 * - Error handling with structured [KavitaResult]
 */
class KavitaAnnotationClient(
    private val client: OkHttpClient = defaultClient(),
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "KavitaAnnotationClient"
        private const val DEFAULT_TIMEOUT_SECONDS = 15L
        private const val JSON_MEDIA_TYPE = "application/json"

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    private var baseUrl: String = ""
    private var apiKey: String? = null
    private var jwtToken: String? = null
    private var username: String? = null
    private var password: String? = null

    /**
     * Configure the client with server connection details.
     */
    fun configure(
        url: String,
        key: String? = null,
        token: String? = null,
        user: String? = null,
        pass: String? = null
    ) {
        baseUrl = url.trimEnd('/')
        apiKey = key
        jwtToken = token
        username = user
        password = pass
    }

    // ==================== Create ====================

    /**
     * POST /api/Annotation/create
     * Create a new annotation.
     */
    suspend fun createAnnotation(
        request: KavitaAnnotationCreateRequest
    ): KavitaResult<KavitaAnnotation> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(request)
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Annotation/create")
                .addHeaders()
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Create annotation failed: ${response.code} $errorBody")
                return@withContext KavitaResult.Error(
                    message = "HTTP ${response.code}: ${response.message}",
                    code = response.code
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext KavitaResult.Error(
                    message = "Empty response body",
                    code = response.code
                )

            val annotation: KavitaAnnotation = gson.fromJson(
                responseBody,
                KavitaAnnotation::class.java
            )
            Log.d(TAG, "Created annotation id=${annotation.id}")
            KavitaResult.Success(annotation)
        } catch (e: IOException) {
            Log.e(TAG, "Network error creating annotation: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error creating annotation: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }

    // ==================== Update ====================

    /**
     * POST /api/Annotation/update
     * Update an existing annotation (comment, spoiler, color).
     */
    suspend fun updateAnnotation(
        request: KavitaAnnotationUpdateRequest
    ): KavitaResult<KavitaAnnotation> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(request)
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Annotation/update")
                .addHeaders()
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Update annotation failed: ${response.code} $errorBody")
                return@withContext KavitaResult.Error(
                    message = "HTTP ${response.code}: ${response.message}",
                    code = response.code
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext KavitaResult.Error(
                    message = "Empty response body",
                    code = response.code
                )

            val annotation: KavitaAnnotation = gson.fromJson(
                responseBody,
                KavitaAnnotation::class.java
            )
            Log.d(TAG, "Updated annotation id=${annotation.id}")
            KavitaResult.Success(annotation)
        } catch (e: IOException) {
            Log.e(TAG, "Network error updating annotation: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error updating annotation: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }

    // ==================== Delete ====================

    /**
     * POST /api/Annotation/delete
     * Delete an annotation.
     */
    suspend fun deleteAnnotation(
        annotationId: Int
    ): KavitaResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = KavitaAnnotationDeleteRequest(id = annotationId)
            val jsonBody = gson.toJson(request)
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Annotation/delete")
                .addHeaders()
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Delete annotation failed: ${response.code} $errorBody")
                return@withContext KavitaResult.Error(
                    message = "HTTP ${response.code}: ${response.message}",
                    code = response.code
                )
            }

            Log.d(TAG, "Deleted annotation id=$annotationId")
            KavitaResult.Success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Network error deleting annotation: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error deleting annotation: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }

    // ==================== Get Chapter Annotations ====================

    /**
     * GET /api/Annotation/all?chapterId=X
     * Get all annotations for a chapter.
     */
    suspend fun getChapterAnnotations(
        chapterId: Int
    ): KavitaResult<List<KavitaAnnotation>> = withContext(Dispatchers.IO) {
        try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Annotation/all?chapterId=$chapterId")
                .addHeaders()
                .get()
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Get chapter annotations failed: ${response.code} $errorBody")
                return@withContext KavitaResult.Error(
                    message = "HTTP ${response.code}: ${response.message}",
                    code = response.code
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext KavitaResult.Error(
                    message = "Empty response body",
                    code = response.code
                )

            val type = object : TypeToken<List<KavitaAnnotation>>() {}.type
            val annotations: List<KavitaAnnotation> = gson.fromJson(responseBody, type)
            Log.d(TAG, "Fetched ${annotations.size} annotations for chapter $chapterId")
            KavitaResult.Success(annotations)
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching chapter annotations: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching chapter annotations: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }

    // ==================== Get Series Annotations ====================

    /**
     * GET /api/Annotation/all-for-series?seriesId=X
     * Get all annotations for a series.
     */
    suspend fun getSeriesAnnotations(
        seriesId: Int
    ): KavitaResult<List<KavitaAnnotation>> = withContext(Dispatchers.IO) {
        try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Annotation/all-for-series?seriesId=$seriesId")
                .addHeaders()
                .get()
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Get series annotations failed: ${response.code} $errorBody")
                return@withContext KavitaResult.Error(
                    message = "HTTP ${response.code}: ${response.message}",
                    code = response.code
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext KavitaResult.Error(
                    message = "Empty response body",
                    code = response.code
                )

            val type = object : TypeToken<List<KavitaAnnotation>>() {}.type
            val annotations: List<KavitaAnnotation> = gson.fromJson(responseBody, type)
            Log.d(TAG, "Fetched ${annotations.size} annotations for series $seriesId")
            KavitaResult.Success(annotations)
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching series annotations: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching series annotations: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }

    // ==================== Like/Unlike ====================

    /**
     * POST /api/Annotation/like
     * Like an annotation.
     */
    suspend fun likeAnnotation(
        annotationId: Int
    ): KavitaResult<Unit> = postLikeAction("like", annotationId)

    /**
     * POST /api/Annotation/unlike
     * Unlike an annotation.
     */
    suspend fun unlikeAnnotation(
        annotationId: Int
    ): KavitaResult<Unit> = postLikeAction("unlike", annotationId)

    private suspend fun postLikeAction(
        action: String,
        annotationId: Int
    ): KavitaResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = KavitaAnnotationLikeRequest(annotationId = annotationId)
            val jsonBody = gson.toJson(request)
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Annotation/$action")
                .addHeaders()
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "$action annotation failed: ${response.code} $errorBody")
                return@withContext KavitaResult.Error(
                    message = "HTTP ${response.code}: ${response.message}",
                    code = response.code
                )
            }

            Log.d(TAG, "${action.replaceFirstChar { it.uppercase() }} annotation id=$annotationId")
            KavitaResult.Success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Network error on $action: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error on $action: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }

    // ==================== Export ====================

    /**
     * GET /api/Annotation/export?chapterId=X
     * Export annotations for a chapter.
     */
    suspend fun exportAnnotations(
        chapterId: Int
    ): KavitaResult<KavitaAnnotationExport> = withContext(Dispatchers.IO) {
        try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Annotation/export?chapterId=$chapterId")
                .addHeaders()
                .get()
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Export annotations failed: ${response.code} $errorBody")
                return@withContext KavitaResult.Error(
                    message = "HTTP ${response.code}: ${response.message}",
                    code = response.code
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext KavitaResult.Error(
                    message = "Empty response body",
                    code = response.code
                )

            val export: KavitaAnnotationExport = gson.fromJson(
                responseBody,
                KavitaAnnotationExport::class.java
            )
            Log.d(
                TAG,
                "Exported ${export.annotations.size} annotations for chapter $chapterId"
            )
            KavitaResult.Success(export)
        } catch (e: IOException) {
            Log.e(TAG, "Network error exporting annotations: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error exporting annotations: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }

    // ==================== Headers ====================

    /**
     * Add authentication headers to the request.
     * Priority: API key > JWT token > HTTP Basic auth.
     */
    private fun Request.Builder.addHeaders(): Request.Builder {
        header("User-Agent", "Mimiral/0.1.0")
        header("Accept", "application/json")

        when {
            !apiKey.isNullOrBlank() -> {
                header("X-Api-Key", apiKey!!)
            }
            !jwtToken.isNullOrBlank() -> {
                header("Authorization", "Bearer $jwtToken")
            }
            !username.isNullOrBlank() && !password.isNullOrBlank() -> {
                header(
                    "Authorization",
                    Credentials.basic(username!!, password!!)
                )
            }
        }
        return this
    }
}
