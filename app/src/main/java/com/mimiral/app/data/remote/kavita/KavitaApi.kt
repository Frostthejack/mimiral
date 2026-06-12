package com.mimiral.app.data.remote.kavita

import com.mimiral.app.data.remote.KavitaServerInfo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
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
     * Authenticate with username/password (alias for login).
     * POST /api/Account/login — some code paths call login() with (username, password).
     */
    @POST("api/Account/login")
    suspend fun loginWithCredentials(
        @Body request: KavitaLoginRequest
    ): Response<KavitaLoginResponse>

    /**
     * Test connection to the Kavita server using the base URL.
     * GET /api/Server/info — lightweight connectivity check.
     */
    @GET("api/Server/info")
    suspend fun testConnection(): Response<KavitaServerInfo>

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
     * Save reading progress for a chapter (legacy/simple model).
     * POST /api/Reader/progress
     *
     * @param progress The reading progress to save
     */
    @POST("api/Reader/progress")
    suspend fun saveProgress(@Body progress: KavitaReadingProgress): Response<Unit>

    /**
     * Get reading progress for a chapter (legacy/simple model).
     * GET /api/Reader/progress?chapterId={chapterId}
     *
     * @param chapterId The chapter ID
     * @return Reading progress
     */
    @GET("api/Reader/progress")
    suspend fun getProgress(
        @Query("chapterId") chapterId: Int
    ): Response<KavitaReadingProgress>

    // ==================== Reading Progress (full DTO) ====================

    /**
     * Push reading progress using the full ProgressDto.
     * POST /api/Reader/progress
     *
     * @param progress Full progress DTO with volumeId, chapterId, pageNum,
     *   seriesId, libraryId, bookScrollId, lastModifiedUtc
     * @return Success indicator
     */
    @POST("api/Reader/progress")
    suspend fun pushProgressDto(
        @Body progress: KavitaProgressDto
    ): Response<KavitaReaderResponse>

    /**
     * Get reading progress for a chapter using the full DTO response.
     * GET /api/Reader/get-progress?chapterId={chapterId}
     *
     * @param chapterId The Kavita chapter ID
     * @return Full progress response with bookScrollId and lastModifiedUtc
     */
    @GET("api/Reader/get-progress")
    suspend fun getProgressDto(
        @Query("chapterId") chapterId: Int
    ): Response<KavitaProgressResponseDto>

    /**
     * Get the user's continue-reading point across all series.
     * Used on app start for the "Continue Reading" feature.
     * GET /api/Reader/continue-point
     *
     * @return The series/chapter/page where the user last left off
     */
    @GET("api/Reader/continue-point")
    suspend fun getContinuePoint(): Response<KavitaContinuePointDto>

    // ==================== Mark Read / Unread ====================

    /**
     * Download a book file from Kavita.
     * GET /api/download/chapter?chapterId={chapterId}
     *
     * @param chapterId The chapter ID to download
     * @return Raw bytes of the book file
     */
    @GET("api/download/chapter")
    suspend fun downloadBook(
        @Query("chapterId") chapterId: Int
    ): Response<okhttp3.ResponseBody>

    /**
     * Download a series cover image from Kavita.
     * GET /api/image/series-cover?seriesId={seriesId}
     *
     * @param seriesId The series ID
     * @return Raw bytes of the cover image
     */
    @GET("api/image/series-cover")
    suspend fun downloadSeriesCover(
        @Query("seriesId") seriesId: Int
    ): Response<okhttp3.ResponseBody>

    /**
     * Download a book/volume cover image from Kavita.
     * GET /api/image/book-cover?chapterId={chapterId}
     *
     * @param chapterId The chapter ID
     * @return Raw bytes of the book cover image
     */
    @GET("api/image/book-cover")
    suspend fun downloadBookCover(
        @Query("chapterId") chapterId: Int
    ): Response<okhttp3.ResponseBody>

    // ==================== Bookmarks (hand-rolled operations) ====================

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

    // ==================== Next / Prev Chapter ====================

    /**
     * Get the next chapter after the current one for seamless end-of-chapter navigation.
     * GET /api/Reader/next-chapter?seriesId={seriesId}&volumeId={volumeId}&chapterId={chapterId}
     *
     * @param seriesId The series ID
     * @param volumeId The current volume ID
     * @param chapterId The current chapter ID
     * @return Next chapter info (id=-1 if no next chapter)
     */
    @GET("api/Reader/next-chapter")
    suspend fun getNextChapter(
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("chapterId") chapterId: Int
    ): Response<KavitaNextChapterDto>

    /**
     * Get the previous chapter before the current one for start-of-chapter navigation.
     * GET /api/Reader/prev-chapter?seriesId={seriesId}&volumeId={volumeId}&chapterId={chapterId}
     *
     * @param seriesId The series ID
     * @param volumeId The current volume ID
     * @param chapterId The current chapter ID
     * @return Previous chapter info (id=-1 if no prev chapter)
     */
    @GET("api/Reader/prev-chapter")
    suspend fun getPrevChapter(
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("chapterId") chapterId: Int
    ): Response<KavitaPrevChapterDto>

    // ==================== Time Left ====================

    /**
     * Get estimated reading time remaining for a series.
     * GET /api/Reader/time-left?seriesId={seriesId}&libraryId={libraryId}
     *
     * @param seriesId The series ID
     * @param libraryId The library ID
     * @return Time remaining estimate with pages left
     */
    @GET("api/Reader/time-left")
    suspend fun getTimeLeft(
        @Query("seriesId") seriesId: Int,
        @Query("libraryId") libraryId: Int
    ): Response<KavitaTimeLeftDto>

    // ==================== On Deck ====================

    /**
     * Get series that the user is actively reading, sorted by most recent.
     * Used for the "On Deck" home shelf.
     * GET /api/Series/on-deck
     *
     * @return List of series currently being read
     */
    @GET("api/Series/on-deck")
    suspend fun getOnDeck(): Response<List<KavitaOnDeckDto>>

    // ==================== Stats ====================

    /**
     * Get reading activity (pages read per day).
     * GET /api/Stats/reading-activity
     */
    @GET("api/Stats/reading-activity")
    suspend fun getReadingActivity(): Response<List<KavitaReadingActivity>>

    /**
     * Get genre breakdown.
     * GET /api/Stats/genre-breakdown
     */
    @GET("api/Stats/genre-breakdown")
    suspend fun getGenreBreakdown(): Response<List<KavitaGenreBreakdown>>

    /**
     * Get pages per year.
     * GET /api/Stats/pages-per-year
     */
    @GET("api/Stats/pages-per-year")
    suspend fun getPagesPerYear(): Response<List<KavitaPagesPerYear>>

    /**
     * Get reading pace trend.
     * GET /api/Stats/reading-pace
     */
    @GET("api/Stats/reading-pace")
    suspend fun getReadingPace(): Response<List<KavitaReadingPace>>

    /**
     * Get favorite authors.
     * GET /api/Stats/favorite-authors
     */
    @GET("api/Stats/favorite-authors")
    suspend fun getFavoriteAuthors(): Response<List<KavitaFavoriteAuthor>>

    /**
     * Get series reading history.
     * GET /api/Stats/reading-history/series/{seriesId}
     */
    @GET("api/Stats/reading-history/series/{seriesId}")
    suspend fun getSeriesReadingHistory(
        @Path("seriesId") seriesId: Int
    ): Response<KavitaSeriesReadingHistory>

    // ==================== Bookmarks ====================

    /**
     * Create (toggle on) a bookmark for a page in a chapter.
     * POST /api/Reader/bookmark
     *
     * @param request The bookmark data (page, chapterId, seriesId, libraryId)
     */
    @POST("api/Reader/bookmark")
    suspend fun createBookmark(
        @Body request: KavitaBookmarkRequest
    ): Response<Unit>

    /**
     * Remove (toggle off) a bookmark for a page in a chapter.
     * POST /api/Reader/unbookmark
     *
     * @param request The unbookmark data (page, chapterId, seriesId, libraryId)
     */
    @POST("api/Reader/unbookmark")
    suspend fun removeBookmark(
        @Body request: KavitaUnbookmarkRequest
    ): Response<Unit>

    /**
     * Get all bookmarks for a specific chapter.
     * GET /api/Reader/chapter-bookmarks?chapterId={chapterId}
     *
     * @param chapterId The chapter ID
     * @return List of chapter bookmarks (page numbers)
     */
    @GET("api/Reader/chapter-bookmarks")
    suspend fun getChapterBookmarks(
        @Query("chapterId") chapterId: Int
    ): Response<List<KavitaChapterBookmark>>

    /**
     * Get all bookmarks for the current user.
     * GET /api/Reader/all-bookmarks
     * Returns bookmarks grouped with series/volume/chapter context.
     *
     * @return List of all bookmarks with full context
     */
    @GET("api/Reader/all-bookmarks")
    suspend fun getAllBookmarks(): Response<List<KavitaBookmarkDto>>

    /**
     * Get all bookmarks for a specific series.
     * GET /api/Reader/series-bookmarks?seriesId={seriesId}
     *
     * @param seriesId The series ID
     * @return Series bookmarks with context
     */
    @GET("api/Reader/series-bookmarks")
    suspend fun getSeriesBookmarks(
        @Query("seriesId") seriesId: Int
    ): Response<KavitaSeriesBookmarksDto>

    /**
     * Export all bookmarks.
     * GET /api/Download/bookmarks
     * Returns bookmarks as a downloadable file.
     *
     * @return Raw response body for file download
     */
    @GET("api/Download/bookmarks")
    suspend fun exportBookmarks(): Response<okhttp3.ResponseBody>

    // ==================== Annotations ====================

    /**
     * Create a new annotation.
     * POST /api/Annotation/create
     *
     * @param request The annotation to create
     * @return The created annotation
     */
    @POST("api/Annotation/create")
    suspend fun createAnnotation(
        @Body request: KavitaAnnotationCreateRequest
    ): Response<KavitaAnnotation>

    /**
     * Update an existing annotation.
     * POST /api/Annotation/update
     *
     * @param request The update fields
     * @return The updated annotation
     */
    @POST("api/Annotation/update")
    suspend fun updateAnnotation(
        @Body request: KavitaAnnotationUpdateRequest
    ): Response<KavitaAnnotation>

    /**
     * Delete an annotation.
     * POST /api/Annotation/delete
     *
     * @param request The deletion request containing the annotation ID
     */
    @POST("api/Annotation/delete")
    suspend fun deleteAnnotation(
        @Body request: KavitaAnnotationDeleteRequest
    ): Response<Unit>

    /**
     * Get all annotations for a chapter.
     * GET /api/Annotation/all?chapterId={chapterId}
     *
     * @param chapterId The chapter ID
     * @return List of annotations
     */
    @GET("api/Annotation/all")
    suspend fun getChapterAnnotations(
        @Query("chapterId") chapterId: Int
    ): Response<List<KavitaAnnotation>>

    /**
     * Get all annotations for a series.
     * GET /api/Annotation/all-for-series?seriesId={seriesId}
     *
     * @param seriesId The series ID
     * @return List of annotations
     */
    @GET("api/Annotation/all-for-series")
    suspend fun getSeriesAnnotations(
        @Query("seriesId") seriesId: Int
    ): Response<List<KavitaAnnotation>>

    /**
     * Like an annotation.
     * POST /api/Annotation/like
     *
     * @param request The like request containing the annotation ID
     */
    @POST("api/Annotation/like")
    suspend fun likeAnnotation(
        @Body request: KavitaAnnotationLikeRequest
    ): Response<Unit>

    /**
     * Unlike an annotation.
     * POST /api/Annotation/unlike
     *
     * @param request The unlike request containing the annotation ID
     */
    @POST("api/Annotation/unlike")
    suspend fun unlikeAnnotation(
        @Body request: KavitaAnnotationLikeRequest
    ): Response<Unit>

    /**
     * Export annotations for a chapter.
     * GET /api/Annotation/export?chapterId={chapterId}
     *
     * @param chapterId The chapter ID
     * @return Exported annotations
     */
    @GET("api/Annotation/export")
    suspend fun exportAnnotations(
        @Query("chapterId") chapterId: Int
    ): Response<KavitaAnnotationExport>

    // ==================== Rating ====================

    /**
     * Rate a series (1-5 stars).
     * POST /api/Rating/series
     *
     * @param request Series rating request
     * @return Rating response with user + community rating
     */
    @POST("api/Rating/series")
    suspend fun rateSeries(
        @Body request: KavitaSeriesRatingRequest
    ): Response<KavitaRatingResponse>

    /**
     * Rate a chapter (1-5 stars).
     * POST /api/Rating/chapter
     *
     * @param request Chapter rating request
     * @return Rating response with user + community rating
     */
    @POST("api/Rating/chapter")
    suspend fun rateChapter(
        @Body request: KavitaChapterRatingRequest
    ): Response<KavitaRatingResponse>

    /**
     * Get the overall/community rating for a series.
     * GET /api/Rating/overall-series?seriesId={seriesId}
     *
     * @param seriesId The series ID
     * @return Overall community rating
     */
    @GET("api/Rating/overall-series")
    suspend fun getOverallSeriesRating(
        @Query("seriesId") seriesId: Int
    ): Response<KavitaOverallSeriesRating>

    // ==================== Reviews ====================

    /**
     * Write a review for a series.
     * POST /api/Review/series
     *
     * @param request Review request body
     * @return The created review
     */
    @POST("api/Review/series")
    suspend fun reviewSeries(
        @Body request: KavitaReviewRequest
    ): Response<KavitaReview>

    /**
     * Write a review for a chapter.
     * POST /api/Review/chapter
     *
     * @param request Review request body
     * @return The created review
     */
    @POST("api/Review/chapter")
    suspend fun reviewChapter(
        @Body request: KavitaReviewRequest
    ): Response<KavitaReview>

    /**
     * Get all reviews (optionally filtered).
     * GET /api/Review/all?seriesId={seriesId}
     *
     * @param seriesId Optional series ID filter
     * @param chapterId Optional chapter ID filter
     * @return List of reviews
     */
    @GET("api/Review/all")
    suspend fun getReviews(
        @Query("seriesId") seriesId: Int? = null,
        @Query("chapterId") chapterId: Int? = null
    ): Response<List<KavitaReview>>

    // ==================== License ====================

    /**
     * Check if Kavita+ license is valid.
     * GET /api/License/valid-license
     *
     * Required before accessing scrobbling endpoints.
     *
     * @return License validation result
     */
    @GET("api/License/valid-license")
    suspend fun validateLicense(): Response<KavitaLicenseStatus>

    // ==================== Scrobbling ====================

    /**
     * Get scrobble settings.
     * GET /api/Scrobbling/scrobble-settings
     *
     * Requires Kavita+ license.
     *
     * @return Current scrobble settings
     */
    @GET("api/Scrobbling/scrobble-settings")
    suspend fun getScrobbleSettings(): Response<KavitaScrobblingSettings>

    /**
     * Update scrobble provider token (re-authentication).
     * POST /api/Scrobbling/update-user-scrobble-provider
     *
     * Use when an external provider token (AniList, MAL, etc.) has expired.
     *
     * @param request Provider name and new token
     */
    @POST("api/Scrobbling/update-user-scrobble-provider")
    suspend fun updateScrobbleProvider(
        @Body request: KavitaUpdateScrobbleProviderRequest
    ): Response<Unit>

    /**
     * Get scrobble errors.
     * GET /api/Scrobbling/scrobble-errors
     *
     * Returns a list of failed scrobble attempts that can be retried.
     *
     * @return List of scrobble errors
     */
    @GET("api/Scrobbling/scrobble-errors")
    suspend fun getScrobbleErrors(): Response<List<KavitaScrobbleError>>

    /**
     * Retry a failed scrobble.
     * POST /api/Scrobbling/retry-scrobble
     *
     * @param errorId The scrobble error ID to retry
     */
    @POST("api/Scrobbling/retry-scrobble/{errorId}")
    suspend fun retryScrobble(
        @Path("errorId") errorId: Int
    ): Response<Unit>

    /**
     * Add a scrobble hold on a series.
     * POST /api/Scrobbling/add-hold
     *
     * Prevents scrobbling for the specified series.
     *
     * @param seriesId The series ID to hold
     */
    @POST("api/Scrobbling/add-hold")
    suspend fun addScrobbleHold(
        @Body seriesId: Int
    ): Response<Unit>

    /**
     * Remove a scrobble hold from a series.
     * POST /api/Scrobbling/remove-hold
     *
     * Re-enables scrobbling for the specified series.
     *
     * @param seriesId The series ID to unhold
     */
    @POST("api/Scrobbling/remove-hold")
    suspend fun removeScrobbleHold(
        @Body seriesId: Int
    ): Response<Unit>

    /**
     * Get all series with scrobble holds.
     * GET /api/Scrobbling/holds
     *
     * @return List of series IDs with active holds
     */
    @GET("api/Scrobbling/holds")
    suspend fun getScrobbleHolds(): Response<List<Int>>

    // ==================== Want To Read ====================

    /**
     * Add a series to the Want To Read list.
     * POST /api/want-to-read/add-series
     *
     * @param request Contains the series ID to add
     */
    @POST("api/want-to-read/add-series")
    suspend fun addToWantToRead(
        @Body request: KavitaWantToReadAddRequest
    ): Response<Unit>

    /**
     * Remove a series from the Want To Read list.
     * POST /api/want-to-read/remove-series
     *
     * @param request Contains the series ID to remove
     */
    @POST("api/want-to-read/remove-series")
    suspend fun removeFromWantToRead(
        @Body request: KavitaWantToReadRemoveRequest
    ): Response<Unit>

    /**
     * Get the Want To Read list with filtering and pagination.
     * GET /api/want-to-read
     *
     * @param pageNumber Page number (0-based)
     * @param pageSize Items per page
     * @param searchQuery Optional search query filter
     * @param libraryId Optional library ID filter
     * @param sortBy Sort field (SortName, CreatedDate, LastChapterAdded, Pages)
     * @param sortDirection Sort direction (Asc, Desc)
     * @return Paginated Want To Read response
     */
    @GET("api/want-to-read")
    suspend fun getWantToRead(
        @Query("PageNumber") pageNumber: Int = 0,
        @Query("PageSize") pageSize: Int = 20,
        @Query("SearchQuery") searchQuery: String? = null,
        @Query("LibraryId") libraryId: Int? = null,
        @Query("SortBy") sortBy: String? = null,
        @Query("SortDirection") sortDirection: String? = null
    ): Response<KavitaWantToReadResponse>

    /**
     * Run auto-cleanup on the Want To Read list.
     * Removes series that have been fully read.
     * POST /api/Server/cleanup-want-to-read
     */
    @POST("api/Server/cleanup-want-to-read")
    suspend fun cleanupWantToRead(): Response<Unit>

    // ==================== Collections ====================

    /**
     * Get all collections.
     * GET /api/Collection
     *
     * @return List of all collections with cover images
     */
    @GET("api/Collection")
    suspend fun getCollections(): Response<List<KavitaCollection>>

    /**
     * Get series in a collection (paginated).
     * GET /api/Series/series-by-collection
     *
     * @param collectionId The collection ID
     * @param pageNumber Page number for pagination
     * @param pageSize Items per page
     * @return Paginated series list for the collection
     */
    @GET("api/Series/series-by-collection")
    suspend fun getSeriesByCollection(
        @Query("collectionId") collectionId: Int,
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): Response<KavitaCollectionSeriesPage>

    /**
     * Create or update a collection by adding/removing series.
     * POST /api/Collection/update-series
     *
     * When tagId=0, creates a new collection.
     * When tagId>0, updates an existing collection's series membership.
     *
     * @param request The collection update request
     */
    @POST("api/Collection/update-series")
    suspend fun updateCollectionSeries(
        @Body request: KavitaCollectionUpdateRequest
    ): Response<Unit>

    /**
     * Update series membership for an existing collection (add/remove).
     * POST /api/Collection/update-for-series
     *
     * @param request Series IDs and collection ID
     */
    @POST("api/Collection/update-for-series")
    suspend fun updateForSeries(
        @Body request: KavitaCollectionSeriesRequest
    ): Response<Unit>

    /**
     * Update collection metadata (title, summary, promoted).
     * POST /api/Collection/update
     *
     * @param request The collection edit request
     */
    @POST("api/Collection/update")
    suspend fun updateCollection(
        @Body request: KavitaCollectionEditRequest
    ): Response<Unit>

    /**
     * Delete a collection.
     * DELETE /api/Collection
     *
     * @param tagId The collection ID to delete
     */
    @DELETE("api/Collection")
    suspend fun deleteCollection(
        @Query("tagId") tagId: Int
    ): Response<Unit>

    // ==================== Reading Lists ====================

    /**
     * Browse all reading lists.
     * GET /api/ReadingList/lists
     *
     * @return List of reading lists
     */
    @GET("api/ReadingList/lists")
    suspend fun getReadingLists(): Response<List<KavitaReadingList>>

    /**
     * Get items in a reading list with read/unread status and progress.
     * GET /api/ReadingList/items
     *
     * @param readingListId The reading list ID
     * @param pageNumber Page number (0-based)
     * @param pageSize Items per page
     * @return Paginated reading list items
     */
    @GET("api/ReadingList/items")
    suspend fun getReadingListItems(
        @Query("readingListId") readingListId: Int,
        @Query("pageNumber") pageNumber: Int = 0,
        @Query("pageSize") pageSize: Int = 20
    ): Response<KavitaReadingListItemsResponse>

    /**
     * Get the next chapter to read in reading-list order.
     * Use this at chapter end instead of series next-chapter.
     * GET /api/ReadingList/next-chapter
     *
     * @param readingListId The reading list ID
     * @param seriesId Current series ID
     * @param volumeId Current volume ID
     * @param chapterId Current chapter ID
     * @return Next chapter info
     */
    @GET("api/ReadingList/next-chapter")
    suspend fun getReadingListNextChapter(
        @Query("readingListId") readingListId: Int,
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("chapterId") chapterId: Int
    ): Response<KavitaReadingListNextChapter>

    /**
     * Create a new reading list.
     * POST /api/ReadingList/create
     *
     * @param request Create request with name and optional summary
     */
    @POST("api/ReadingList/create")
    suspend fun createReadingList(
        @Body request: KavitaReadingListCreateRequest
    ): Response<KavitaReadingList>

    /**
     * Add all chapters from a series to a reading list.
     * POST /api/ReadingList/update-by-series
     *
     * @param request Update request with reading list and series IDs
     */
    @POST("api/ReadingList/update-by-series")
    suspend fun updateReadingListBySeries(
        @Body request: KavitaReadingListUpdateBySeriesRequest
    ): Response<Unit>

    /**
     * Add multiple items (series, chapters, volumes) to a reading list.
     * POST /api/ReadingList/update-by-multiple
     *
     * @param request Update request with reading list ID and item IDs
     */
    @POST("api/ReadingList/update-by-multiple")
    suspend fun updateReadingListByMultiple(
        @Body request: KavitaReadingListUpdateByMultipleRequest
    ): Response<Unit>

    /**
     * Reorder an item's position in a reading list.
     * POST /api/ReadingList/update-position
     *
     * @param request Position update request
     */
    @POST("api/ReadingList/update-position")
    suspend fun updateReadingListPosition(
        @Body request: KavitaReadingListUpdatePositionRequest
    ): Response<Unit>

    /**
     * Remove read items from a reading list (cleanup).
     * POST /api/ReadingList/remove-read
     *
     * @param request Remove-read request with reading list ID
     */
    @POST("api/ReadingList/remove-read")
    suspend fun removeReadFromReadingList(
        @Body request: KavitaReadingListRemoveReadRequest
    ): Response<Unit>

    /**
     * Update a reading list's name and/or summary.
     * POST /api/ReadingList/update
     *
     * @param request Update request
     */
    @POST("api/ReadingList/update")
    suspend fun updateReadingList(
        @Body request: KavitaReadingListUpdateRequest
    ): Response<Unit>

    /**
     * Delete a reading list.
     * POST /api/ReadingList/delete
     *
     * @param request Delete request with reading list ID
     */
    @POST("api/ReadingList/delete")
    suspend fun deleteReadingList(
        @Body request: KavitaReadingListDeleteRequest
    ): Response<Unit>
}
