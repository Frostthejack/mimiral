package com.mimiral.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.mimiral.app.data.remote.kavita.KavitaNextChapterDto
import com.mimiral.app.data.remote.kavita.KavitaPrevChapterDto

data class ChapterNavigationState(
    val nextChapter: KavitaNextChapterDto? = null,
    val prevChapter: KavitaPrevChapterDto? = null,
    val isLoading: Boolean = false,
    val seriesId: Int = 0,
    val volumeId: Int = 0,
    val chapterId: Int = 0
) {
    val hasNextChapter: Boolean get() = nextChapter != null && nextChapter.id > 0
    val hasPrevChapter: Boolean get() = prevChapter != null && prevChapter.id > 0
}

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
            Spacer(modifier = Modifier.weight(1f))
        }

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
