package com.mimiral.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for Kavita Reader API endpoints.
 * Base URL should be the Kavita server URL (e.g., http://localhost:5000).
 */
interface KavitaSyncApi {

    /**
     * Push reading progress to Kavita.
     * POST /api/Reader/progress
     */
    @POST("api/Reader/progress")
    suspend fun pushProgress(
        @Body request: KavitaProgressRequest
    ): Response<KavitaProgressResponse>

    /**
     * Pull reading progress from Kavita.
     * GET /api/Reader/get-progress?seriesId={seriesId}&libraryId={libraryId}
     */
    @GET("api/Reader/get-progress")
    suspend fun pullProgress(
        @Query("seriesId") seriesId: Int,
        @Query("libraryId") libraryId: Int
    ): Response<KavitaProgressData>

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
}
