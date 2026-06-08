package com.mimiral.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.kavita.KavitaOverallSeriesRating
import com.mimiral.app.data.remote.kavita.KavitaRatingRepository
import com.mimiral.app.data.remote.kavita.KavitaReview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for rating and review features.
 */
data class RatingReviewUiState(
    val isLoading: Boolean = false,
    val userRating: Int = 0,
    val communityRating: Double = 0.0,
    val communityRatingsCount: Int = 0,
    val reviews: List<KavitaReview> = emptyList(),
    val isSubmittingRating: Boolean = false,
    val isSubmittingReview: Boolean = false,
    val errorMessage: String? = null,
    val reviewSubmitSuccess: Boolean = false
)

/**
 * ViewModel for Kavita rating and review operations.
 *
 * Manages:
 * - Series star rating (user + community)
 * - Review submission and retrieval
 * - Chapter-level rating and reviews (optional)
 */
@HiltViewModel
class RatingReviewViewModel @Inject constructor(
    private val ratingRepository: KavitaRatingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RatingReviewUiState())
    val uiState: StateFlow<RatingReviewUiState> = _uiState.asStateFlow()

    /**
     * Load rating and reviews for a series.
     *
     * @param seriesId The Kavita series ID
     */
    fun loadForSeries(seriesId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Load community/overall rating
            val overallRating = ratingRepository.getOverallSeriesRating(seriesId)
            if (overallRating != null) {
                _uiState.value = _uiState.value.copy(
                    communityRating = overallRating.totalRating,
                    communityRatingsCount = overallRating.ratingsCount
                )
            }

            // Load reviews for this series
            val reviews = ratingRepository.getReviews(seriesId = seriesId)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                reviews = reviews
            )
        }
    }

    /**
     * Rate a series (1-5 stars).
     *
     * @param seriesId The series ID
     * @param rating The rating (1-5, or 0 to remove)
     */
    fun rateSeries(seriesId: Int, rating: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingRating = true, errorMessage = null)

            val response = ratingRepository.rateSeries(seriesId, rating)
            if (response != null) {
                _uiState.value = _uiState.value.copy(
                    userRating = response.userRating,
                    communityRating = response.totalRating,
                    communityRatingsCount = response.ratingsCount,
                    isSubmittingRating = false
                )

                // Refresh overall rating after user rates
                val overall = ratingRepository.getOverallSeriesRating(seriesId)
                if (overall != null) {
                    _uiState.value = _uiState.value.copy(
                        communityRating = overall.totalRating,
                        communityRatingsCount = overall.ratingsCount
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isSubmittingRating = false,
                    errorMessage = "Failed to submit rating"
                )
            }
        }
    }

    /**
     * Rate a chapter (1-5 stars).
     *
     * @param chapterId The chapter ID
     * @param rating The rating (1-5, or 0 to remove)
     */
    fun rateChapter(chapterId: Int, rating: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingRating = true, errorMessage = null)

            val response = ratingRepository.rateChapter(chapterId, rating)
            if (response != null) {
                _uiState.value = _uiState.value.copy(
                    userRating = response.userRating,
                    isSubmittingRating = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSubmittingRating = false,
                    errorMessage = "Failed to submit rating"
                )
            }
        }
    }

    /**
     * Submit a review for a series.
     *
     * @param seriesId The series ID
     * @param reviewBody The review text
     * @param tagline Optional short tagline
     */
    fun submitSeriesReview(
        seriesId: Int,
        reviewBody: String,
        tagline: String? = null
    ) {
        if (reviewBody.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmittingReview = true,
                errorMessage = null,
                reviewSubmitSuccess = false
            )

            val review = ratingRepository.reviewSeries(seriesId, reviewBody, tagline)
            if (review != null) {
                // Refresh reviews list
                val reviews = ratingRepository.getReviews(seriesId = seriesId)
                _uiState.value = _uiState.value.copy(
                    isSubmittingReview = false,
                    reviews = reviews,
                    reviewSubmitSuccess = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSubmittingReview = false,
                    errorMessage = "Failed to submit review"
                )
            }
        }
    }

    /**
     * Submit a review for a chapter.
     *
     * @param chapterId The chapter ID
     * @param reviewBody The review text
     * @param tagline Optional short tagline
     */
    fun submitChapterReview(
        chapterId: Int,
        reviewBody: String,
        tagline: String? = null
    ) {
        if (reviewBody.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmittingReview = true,
                errorMessage = null,
                reviewSubmitSuccess = false
            )

            val review = ratingRepository.reviewChapter(chapterId, reviewBody, tagline)
            if (review != null) {
                // Refresh reviews list
                val reviews = ratingRepository.getReviews(chapterId = chapterId)
                _uiState.value = _uiState.value.copy(
                    isSubmittingReview = false,
                    reviews = reviews,
                    reviewSubmitSuccess = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSubmittingReview = false,
                    errorMessage = "Failed to submit review"
                )
            }
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Clear review submit success flag.
     */
    fun clearReviewSuccess() {
        _uiState.value = _uiState.value.copy(reviewSubmitSuccess = false)
    }
}
