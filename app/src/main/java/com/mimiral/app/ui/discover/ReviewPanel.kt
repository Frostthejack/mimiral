package com.mimiral.app.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimiral.app.data.remote.kavita.KavitaReview

/**
 * A panel for writing and displaying reviews.
 *
 * Contains:
 * - A text field for writing a review (with optional tagline)
 * - A submit button
 * - A list of existing reviews
 *
 * @param reviews List of existing reviews to display
 * @param onSubmitReview Callback when user submits a review. Receives (body, tagline).
 * @param isSubmitting Whether a review is currently being submitted
 * @param showTaglineField Whether to show the optional tagline input
 */
@Composable
fun ReviewPanel(
    reviews: List<KavitaReview>,
    onSubmitReview: (body: String, tagline: String?) -> Unit,
    isSubmitting: Boolean = false,
    showTaglineField: Boolean = true,
    modifier: Modifier = Modifier
) {
    var reviewText by remember { mutableStateOf("") }
    var taglineText by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        // ── Write Review Section ──
        Text(
            text = "Write a Review",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (showTaglineField) {
            OutlinedTextField(
                value = taglineText,
                onValueChange = { taglineText = it },
                label = { Text("Tagline (optional)") },
                placeholder = { Text("A short summary of your review") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = reviewText,
                onValueChange = { reviewText = it },
                label = { Text("Your review") },
                placeholder = { Text("Share your thoughts about this series...") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                shape = RoundedCornerShape(8.dp)
            )

            IconButton(
                onClick = {
                    if (reviewText.isNotBlank()) {
                        onSubmitReview(
                            reviewText.trim(),
                            taglineText.trim().ifBlank { null }
                        )
                        reviewText = ""
                        taglineText = ""
                    }
                },
                enabled = reviewText.isNotBlank() && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Submit review",
                        tint = if (reviewText.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    )
                }
            }
        }

        // ── Existing Reviews ──
        if (reviews.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Reviews (${reviews.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(
                    // Limit height to avoid nested scroll issues
                    (reviews.size * 120).coerceAtMost(400).dp
                )
            ) {
                items(reviews, key = { "review_${it.id}" }) { review ->
                    ReviewCard(review = review)
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No reviews yet. Be the first to share your thoughts!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * A single review card showing reviewer info, tagline, body, and rating.
 */
@Composable
private fun ReviewCard(
    review: KavitaReview,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: username + date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = review.username ?: "Anonymous",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                // Inline star rating if the review includes one
                if (review.rating > 0) {
                    StarRatingWidget(
                        rating = review.rating.toFloat(),
                        onRatingChange = null,
                        starSize = 14.dp,
                        showLabel = false,
                        activeColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Tagline (if present)
            if (!review.tagline.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = review.tagline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Review body
            if (review.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = review.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Date
            if (!review.createdUtc.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = review.createdUtc.take(10), // YYYY-MM-DD
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
