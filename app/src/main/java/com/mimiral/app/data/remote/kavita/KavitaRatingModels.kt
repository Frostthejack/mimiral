package com.mimiral.app.data.remote.kavita

// ==================== Rating Models ====================

/**
 * Request body for rating a series.
 * POST /api/Rating/series
 *
 * @param seriesId The Kavita series ID
 * @param userRating User's rating (1-5 stars, 0 to remove)
 */
data class KavitaSeriesRatingRequest(
    val seriesId: Int,
    val userRating: Int
)

/**
 * Request body for rating a chapter.
 * POST /api/Rating/chapter
 *
 * @param chapterId The Kavita chapter ID
 * @param userRating User's rating (1-5 stars, 0 to remove)
 */
data class KavitaChapterRatingRequest(
    val chapterId: Int,
    val userRating: Int
)

/**
 * Response from rating endpoints.
 * Contains the user's rating for the entity.
 *
 * @param userRating The user's rating (1-5, or 0 if not rated)
 * @param totalRating The community/overall average rating
 * @param ratingsCount Number of ratings submitted
 */
data class KavitaRatingResponse(
    val userRating: Int = 0,
    val totalRating: Double = 0.0,
    val ratingsCount: Int = 0
)

/**
 * Community/overall rating for a series.
 * GET /api/Rating/overall-series?seriesId={seriesId}
 *
 * @param seriesId The Kavita series ID
 * @param totalRating Average rating across all users
 * @param ratingsCount Total number of ratings
 */
data class KavitaOverallSeriesRating(
    val seriesId: Int,
    val totalRating: Double = 0.0,
    val ratingsCount: Int = 0
)

// ==================== Review Models ====================

/**
 * Request body for writing a review.
 * POST /api/Review/series or /api/Review/chapter
 *
 * @param seriesId The Kavita series ID (for series review)
 * @param chapterId The Kavita chapter ID (for chapter review)
 * @param reviewBody The review text content
 * @param tagline Optional short tagline/summary
 */
data class KavitaReviewRequest(
    val seriesId: Int? = null,
    val chapterId: Int? = null,
    val reviewBody: String,
    val tagline: String? = null
)

/**
 * Review response from Kavita API.
 * GET /api/Review/all or returned after POST.
 *
 * @param id The review ID
 * @param seriesId The series ID this review belongs to
 * @param chapterId The chapter ID (if chapter review)
 * @param volumeId The volume ID
 * @param body The review text content
 * @param tagline Optional short tagline/summary
 * @param username Username of the reviewer
 * @param rating Rating given by the reviewer (1-5, or 0)
 * @param createdUtc ISO-8601 creation timestamp
 * @param modifiedUtc ISO-8601 last modification timestamp
 */
data class KavitaReview(
    val id: Int,
    val seriesId: Int = 0,
    val chapterId: Int = 0,
    val volumeId: Int = 0,
    val body: String = "",
    val tagline: String? = null,
    val username: String? = null,
    val rating: Int = 0,
    val createdUtc: String? = null,
    val modifiedUtc: String? = null
)
