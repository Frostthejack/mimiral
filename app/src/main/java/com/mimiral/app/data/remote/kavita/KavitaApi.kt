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
     * POST /api/Account/login (Kavita v0.7+).
     *
     * @param request Login credentials
     * @return JWT token response
     */
    @POST("api/Account/login")
    suspend fun login(@Body request: KavitaLoginRequest): Response<KavitaLoginResponse>

    /**
     * Fallback login endpoint for Kavita versions before v0.7.
     * POST /api/Auth/login
     *
     * @param request Login credentials
     * @return JWT token response
     */
    @POST("api/Auth/login")
    suspend fun loginLegacy(@Body request: KavitaLoginRequest): Response<KavitaLoginResponse>

    /**
     * Authenticate via API key to receive a JWT token.
     * GET /api/Plugin/authenticate — the API key is sent as X-Api-Key header,
     * the server returns a JWT token in the response body.
     *
     * @param apiKey The plugin API key (sent as X-Api-Key header)
     * @return JWT token string in response body
     */
    @GET("api/Plugin/authenticate")
    suspend fun authenticateWithApiKey(
        @Header("X-Api-Key") apiKey: String
    ): Response<String>

    /**
     * Refresh an expired JWT token.
     * POST /api/Account/refresh-token (Kavita v0.7+).
     *
     * The Authorization header carries the Bearer JWT (even if expired),
     * and the Refresh-Token header carries the refresh token.
     *
     * @param token The Bearer JWT (even if expired)
     * @param refreshToken The refresh token
     * @return New JWT token response
     */
    @POST("api/Account/refresh-token")
    suspend fun refreshToken(
        @Header("Authorization") token: String,
        @Header("Refresh-Token") refreshToken: String
    ): Response<KavitaLoginResponse>

    /**
     * Get the OPDS feed URL for the authenticated user.
     * GET /api/Account/opds-url
     *
     * Returns the full OPDS URL including the API key path segment,
     * e.g. "https://kavita.example.com/api/opds/{apiKey}"
     *
     * @return OPDS URL string
     */
    @GET("api/Account/opds-url")
    suspend fun getOpdsUrl(): Response<String>

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
}
