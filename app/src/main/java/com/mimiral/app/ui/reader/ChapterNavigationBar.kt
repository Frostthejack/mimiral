package com.mimiral.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Next/Prev Chapter navigation bar shown at chapter boundaries.
 *
 * At end-of-chapter (last page): shows "Next Chapter" button on the right.
 * At start-of-chapter (first page): shows "Previous Chapter" button on the left.
 * At boundaries: both buttons can appear.
 *
 * @param navigationState Current chapter navigation state
 * @param isAtFirstPage Whether the reader is at the first page of the chapter
 * @param isAtLastPage Whether the reader is at the last page of the chapter
 * @param onPrevChapter Callback to navigate to the previous chapter
 * @param onNextChapter Callback to navigate to the next chapter
 */
@Composable
fun ChapterNavigationBar(
    navigationState: ChapterNavigationState,
    isAtFirstPage: Boolean = false,
    isAtLastPage: Boolean = false,
    onPrevChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {}
) {
    val showPrev = isAtFirstPage && navigationState.hasPrevChapter
    val showNext = isAtLastPage && navigationState.hasNextChapter

    if (!showPrev && !showNext) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous Chapter button (left side, shown at first page)
        if (showPrev) {
            FilledTonalButton(onClick = onPrevChapter) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Chapter",
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = navigationState.prevChapter?.title ?: "Prev Chapter",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        } else {
            // Spacer to keep Next button on the right
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.weight(1f)
            )
        }

        // Next Chapter button (right side, shown at last page)
        if (showNext) {
            FilledTonalButton(onClick = onNextChapter) {
                Text(
                    text = navigationState.nextChapter?.title ?: "Next Chapter",
                    modifier = Modifier.padding(end = 4.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Chapter",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Compact "End of Chapter" banner for use at the bottom of a reader page.
 * Shows next chapter info with a navigation button.
 */
@Composable
fun EndOfChapterBanner(
    nextChapterTitle: String?,
    onNextChapter: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(onClick = onNextChapter) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Next: ${nextChapterTitle ?: "Chapter"}",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
