package com.mimiral.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Kavita Reader API endpoints.
 * Base URL should be the Kavita server URL (resolved from active server config).
 */
interface KavitaSyncApi {

    // ── Progress sync endpoints (Native / Reader) ──

    /**
     * Push reading progress to Kavita (native JWT-authenticated endpoint).
     * POST /api/Reader/progress
     */
    @POST("api/Reader/progress")
    suspend fun pushProgress(
        @Body request: KavitaProgressRequest
    ): Response<KavitaProgressResponse>

    /**
     * Pull reading progress from Kavita (native JWT-authenticated endpoint).
     * GET /api/Reader/get-progress?seriesId={seriesId}&libraryId={libraryId}
     */
    @GET("api/Reader/get-progress")
    suspend fun pullProgress(
        @Query("seriesId") seriesId: Int,
        @Query("libraryId") libraryId: Int
    ): Response<KavitaProgressData>

    // ── KOReader sync endpoints (API key in path) ──

    /**
     * Push reading progress via KOReader API.
     * PUT /api/Koreader/{apiKey}/syncs/progress
     *
     * Authenticates via API key in the path — no JWT required.
     */
    @PUT("api/Koreader/{apiKey}/syncs/progress")
    suspend fun pushKoreaderProgress(
        @Path("apiKey") apiKey: String,
        @Body request: KoreaderBookDto
    ): Response<KavitaProgressResponse>

    /**
     * Pull reading progress via KOReader API.
     * GET /api/Koreader/{apiKey}/syncs/progress/{ebookHash}
     *
     * Returns progress for a specific book identified by its MD5 hash.
     */
    @GET("api/Koreader/{apiKey}/syncs/progress/{ebookHash}")
    suspend fun pullKoreaderProgress(
        @Path("apiKey") apiKey: String,
        @Path("ebookHash") ebookHash: String
    ): Response<KoreaderBookDto>

    /**
     * Authenticate with KOReader API key.
     * GET /api/Koreader/{apiKey}/users/auth
     *
     * Validates the API key and returns user info.
     */
    @GET("api/Koreader/{apiKey}/users/auth")
    suspend fun authKoreader(
        @Path("apiKey") apiKey: String
    ): Response<KavitaLoginResponse>

    // ── Panels sync endpoints (API key as query param) ──

    /**
     * Push reading progress via Panels API.
     * POST /api/Panels/save-progress?apiKey={apiKey}
     *
     * Uses the same ProgressDto shape as Reader/progress, but authenticates
     * via apiKey query parameter — no JWT required.
     */
    @POST("api/Panels/save-progress")
    suspend fun pushPanelsProgress(
        @Query("apiKey") apiKey: String,
        @Body request: PanelsProgressRequest
    ): Response<KavitaProgressResponse>

    /**
     * Pull reading progress via Panels API.
     * GET /api/Panels/get-progress?chapterId={chapterId}&apiKey={apiKey}
     */
    @GET("api/Panels/get-progress")
    suspend fun pullPanelsProgress(
        @Query("chapterId") chapterId: Int,
        @Query("apiKey") apiKey: String
    ): Response<PanelsProgressData>

    // ── Auth endpoints ──

    /**
     * Authenticate with username and password to receive a JWT token.
     * POST /api/Auth/login
     */
    @POST("api/Auth/login")
    suspend fun login(
        @Body request: KavitaLoginRequest
    ): Response<KavitaLoginResponse>

    /**
     * Get server info and validate connectivity.
     * GET /api/Server/info
     */
    @GET("api/Server/info")
    suspend fun getServerInfo(): Response<KavitaServerInfo>

    // ── Series / Volume browsing endpoints ──

    /**
     * Get detailed series info including volumes, chapters, and specials.
     * GET /api/Series/series-detail?seriesId={seriesId}
     */
    @GET("api/Series/series-detail")
    suspend fun getSeriesDetail(
        @Query("seriesId") seriesId: Int
    ): Response<SeriesDetailDto>

    /**
     * Get all volumes for a series with progress info and chapters.
     * GET /api/Series/volumes?seriesId={seriesId}
     */
    @GET("api/Series/volumes")
    suspend fun getVolumes(
        @Query("seriesId") seriesId: Int
    ): Response<List<VolumeDto>>

    /**
     * Get a single volume with progress info and chapters.
     * GET /api/Series/volume?volumeId={volumeId}
     */
    @GET("api/Series/volume")
    suspend fun getVolume(
        @Query("volumeId") volumeId: Int
    ): Response<VolumeDto>

    /**
     * Get basic series info by ID.
     * GET /api/Series/{seriesId}
     */
    @GET("api/Series/{seriesId}")
    suspend fun getSeries(
        @Path("seriesId") seriesId: Int
    ): Response<SeriesDto>
}
