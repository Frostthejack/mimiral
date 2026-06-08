package com.mimiral.app.data.remote.kavita

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service interface for the Kavita API.
 *
 * Supports both JWT Bearer token authentication and API key authentication.
 * Use [KavitaAuthInterceptor] to handle token injection automatically.
 */
interface KavitaApi {

    // ==================== Authentication ====================

    /**
     * Authenticate with username and password to receive a JWT token.
     * POST /api/Auth/login
     *
     * @param request Login credentials
     * @return JWT token response
     */
    @POST("api/Auth/login")
    suspend fun login(@Body request: KavitaLoginRequest): Response<KavitaLoginResponse>

    /**
     * Refresh an expired JWT token.
     * POST /api/Auth/refresh-token
     *
     * @param token The current/expiring JWT token (Bearer)
     * @param refreshToken The refresh token
     * @return New JWT token response
     */
    @POST("api/Auth/refresh-token")
    suspend fun refreshToken(
        @Header("Authorization") token: String,
        @Header("Refresh-Token") refreshToken: String
    ): Response<KavitaLoginResponse>

    // ==================== Server Info ====================

    /**
     * Get server info and validate connectivity.
     * GET /api/Server/info
     *
     * @return Server version and configuration info
     */
    @GET("api/Server/info")
    suspend fun getServerInfo(): Response<KavitaServerInfo>

    // ==================== Libraries ====================

    /**
     * Get all libraries on the server.
     * GET /api/Library
     *
     * @return List of all libraries
     */
    @GET("api/Library")
    suspend fun getLibraries(): Response<List<KavitaLibrary>>

    /**
     * Get a specific library by ID.
     * GET /api/Library/{libraryId}
     *
     * @param libraryId The library ID
     * @return Library details
     */
    @GET("api/Library/{libraryId}")
    suspend fun getLibrary(
        @Path("libraryId") libraryId: Int
    ): Response<KavitaLibrary>

    // ==================== User Info ====================

    /**
     * Get current user info.
     * GET /api/Account/info
     *
     * @return Current user information
     */
    @GET("api/Account/info")
    suspend fun getUserInfo(): Response<KavitaUserInfo>

    /**
     * Generate or retrieve an API key for the current user.
     * POST /api/Account/api-key
     *
     * @return The API key string (plain text in response body)
     */
    @POST("api/Account/api-key")
    suspend fun generateApiKey(): Response<String>

    // ==================== Series ====================

    /**
     * Get all series in a library.
     * GET /api/Series?libraryId={libraryId}
     *
     * @param libraryId The library ID to filter by
     * @param pageNumber Page number for pagination
     * @param pageSize Items per page
     * @return Paginated list of series
     */
    @GET("api/Series")
    suspend fun getSeries(
        @Query("libraryId") libraryId: Int,
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): Response<KavitaPaginatedResponse<KavitaSeries>>

    /**
     * Get a specific series by ID.
     * GET /api/Series/{seriesId}
     *
     * @param seriesId The series ID
     * @return Series details
     */
    @GET("api/Series/{seriesId}")
    suspend fun getSeriesById(
        @Path("seriesId") seriesId: Int
    ): Response<KavitaSeries>

    // ==================== Volumes & Chapters ====================

    /**
     * Get all volumes for a series.
     * GET /api/Series/volumes?seriesId={seriesId}
     *
     * @param seriesId The series ID
     * @return List of volumes
     */
    @GET("api/Series/volumes")
    suspend fun getVolumes(
        @Query("seriesId") seriesId: Int
    ): Response<List<KavitaVolume>>

    /**
     * Get all chapters for a volume.
     * GET /api/Series/chapter?chapterId={chapterId}
     *
     * @param chapterId The chapter ID
     * @return Chapter details
     */
    @GET("api/Series/chapter")
    suspend fun getChapter(
        @Query("chapterId") chapterId: Int
    ): Response<KavitaChapter>

    // ==================== Reading Progress ====================

    /**
     * Save reading progress for a chapter.
     * POST /api/Reader/progress
     *
     * @param progress The reading progress to save
     */
    @POST("api/Reader/progress")
    suspend fun saveProgress(@Body progress: KavitaReadingProgress): Response<Unit>

    /**
     * Get reading progress for a chapter.
     * GET /api/Reader/progress?chapterId={chapterId}
     *
     * @param chapterId The chapter ID
     * @return Reading progress
     */
    @GET("api/Reader/progress")
    suspend fun getProgress(
        @Query("chapterId") chapterId: Int
    ): Response<KavitaReadingProgress>

    // ==================== Mark Read / Unread ====================

    /**
     * Mark a chapter as read.
     * POST /api/Reader/mark-chapter-read
     *
     * @param request Chapter read request
     */
    @POST("api/Reader/mark-chapter-read")
    suspend fun markChapterRead(
        @Body request: KavitaMarkChapterReadRequest
    ): Response<Unit>

    /**
     * Mark a volume as read.
     * POST /api/Reader/mark-volume-read
     *
     * @param request Volume read request
     */
    @POST("api/Reader/mark-volume-read")
    suspend fun markVolumeRead(
        @Body request: KavitaMarkVolumeReadRequest
    ): Response<Unit>

    /**
     * Mark a volume as unread.
     * POST /api/Reader/mark-volume-unread
     *
     * @param request Volume unread request
     */
    @POST("api/Reader/mark-volume-unread")
    suspend fun markVolumeUnread(
        @Body request: KavitaMarkVolumeReadRequest
    ): Response<Unit>

    /**
     * Mark a series as read.
     * POST /api/Series/mark-read
     *
     * @param request Series read request
     */
    @POST("api/Series/mark-read")
    suspend fun markSeriesRead(
        @Body request: KavitaMarkSeriesReadRequest
    ): Response<Unit>

    /**
     * Mark a series as unread.
     * POST /api/Series/mark-unread
     *
     * @param request Series unread request
     */
    @POST("api/Series/mark-unread")
    suspend fun markSeriesUnread(
        @Body request: KavitaMarkSeriesReadRequest
    ): Response<Unit>

    /**
     * Bulk mark multiple series as read.
     * POST /api/Series/mark-multiple-series-read
     *
     * @param request Multiple series read request
     */
    @POST("api/Series/mark-multiple-series-read")
    suspend fun markMultipleSeriesRead(
        @Body request: KavitaMarkMultipleSeriesReadRequest
    ): Response<Unit>

    /**
     * Bulk mark multiple series as unread.
     * POST /api/Series/mark-multiple-series-unread
     *
     * @param request Multiple series unread request
     */
    @POST("api/Series/mark-multiple-series-unread")
    suspend fun markMultipleSeriesUnread(
        @Body request: KavitaMarkMultipleSeriesReadRequest
    ): Response<Unit>

    /**
     * Catch-up: mark all chapters up to a point as read.
     * POST /api/Tachiyomi/mark-chapter-until-as-read
     *
     * @param request Catch-up read request
     */
    @POST("api/Tachiyomi/mark-chapter-until-as-read")
    suspend fun markChapterUntilRead(
        @Body request: KavitaMarkChapterUntilReadRequest
    ): Response<Unit>
}
