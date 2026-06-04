package com.mimiral.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Kavita Reader API endpoints.
 * Base URL should be the Kavita server URL (e.g., http://localhost:5000).
 */
interface KavitaSyncApi {

    // ── Progress sync endpoints ──

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
