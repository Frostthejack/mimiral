package com.mimiral.app.ui.discover

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A reusable star rating widget.
 *
 * Displays 5 stars that can be tapped to set a rating.
 * - Filled stars for the user rating
 * - Half stars for fractional community ratings
 * - Empty stars for unrated positions
 *
 * @param rating Current rating (0-5). For user input, this is an integer.
 * @param onRatingChange Callback when a star is tapped. Null for read-only mode.
 * @param maxRating Maximum number of stars (default 5)
 * @param starSize Size of each star icon
 * @param activeColor Color for filled/half stars
 * @param inactiveColor Color for empty stars
 * @param showLabel Whether to show the numeric label next to stars
 * @param isHalfStar Whether to support half-star display (for community rating)
 * @param enabled Whether interaction is enabled
 */
@Composable
fun StarRatingWidget(
    rating: Float,
    onRatingChange: ((Int) -> Unit)? = null,
    maxRating: Int = 5,
    starSize: Dp = 32.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
    showLabel: Boolean = true,
    isHalfStar: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        for (i in 1..maxRating) {
            val isSelected = i <= rating
            val isHalf = isHalfStar && !isSelected && (i - 0.5f) <= rating

            if (onRatingChange != null && enabled) {
                // Interactive mode — tappable stars
                IconButton(
                    onClick = {
                        // Tap same star again to remove rating
                        val newRating = if (i == rating.toInt() && rating == i.toFloat()) {
                            i - 1 // deselect
                        } else {
                            i
                        }
                        onRatingChange(newRating)
                    },
                    modifier = Modifier.size(starSize + 8.dp) // touch target
                        .semantics {
                            contentDescription = "Rate $i out of $maxRating stars"
                        }
                ) {
                    Icon(
                        imageVector = if (isSelected) {
                            Icons.Default.Star
                        } else if (isHalf) {
                            Icons.Default.StarHalf
                        } else {
                            Icons.Default.StarBorder
                        },
                        contentDescription = null,
                        tint = if (isSelected || isHalf) activeColor else inactiveColor,
                        modifier = Modifier.size(starSize)
                    )
                }
            } else {
                // Read-only mode — display only
                Icon(
                    imageVector = if (isSelected) {
                        Icons.Default.Star
                    } else if (isHalf) {
                        Icons.Default.StarHalf
                    } else {
                        Icons.Default.StarBorder
                    },
                    contentDescription = null,
                    tint = if (isSelected || isHalf) activeColor else inactiveColor,
                    modifier = Modifier.size(starSize)
                )
                if (i < maxRating) {
                    Spacer(modifier = Modifier.width(2.dp))
                }
            }
        }

        if (showLabel && rating > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isHalfStar) {
                    String.format("%.1f", rating)
                } else {
                    rating.toInt().toString()
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A compact rating display row showing user rating and community rating side by side.
 *
 * @param userRating User's own rating (0-5 integer)
 * @param communityRating Community average rating (0-5 float)
 * @param communityRatingsCount Number of community ratings
 * @param onUserRatingChange Callback when user changes their rating. Null for read-only.
 * @param enabled Whether interaction is enabled
 */
@Composable
fun RatingDisplayRow(
    userRating: Int,
    communityRating: Double,
    communityRatingsCount: Int,
    onUserRatingChange: ((Int) -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // User rating (interactive)
        Text(
            text = "Your Rating",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        StarRatingWidget(
            rating = userRating.toFloat(),
            onRatingChange = onUserRatingChange,
            starSize = 24.dp,
            showLabel = false,
            enabled = enabled
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Community rating (read-only with half stars)
        Text(
            text = "Community",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        StarRatingWidget(
            rating = communityRating.toFloat(),
            onRatingChange = null, // read-only
            starSize = 20.dp,
            isHalfStar = true,
            activeColor = MaterialTheme.colorScheme.tertiary,
            showLabel = false
        )
        if (communityRatingsCount > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = String.format("%.1f", communityRating),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = " ($communityRatingsCount)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        } else {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "No ratings",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
