package com.mimiral.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.mimiral.app.data.reader.Sentence

/**
 * Renders page text with highlight overlays applied.
 * Supports long press gesture to trigger text selection for highlighting.
 * Also renders TTS sentence-level highlight when a sentence is actively being read.
 */
@Composable
fun HighlightableText(
    text: String,
    highlights: List<ReaderHighlight>,
    textSettings: TextSettings,
    ttsSentence: Sentence? = null,
    onLongPress: (selectedText: String, startOffset: Int, endOffset: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Build AnnotatedString with highlight spans and TTS sentence highlight applied
    val annotatedText = remember(text, highlights, ttsSentence) {
        buildAnnotatedString {
            append(text)

            // User-created saved highlights
            highlights.forEach { highlight ->
                val color = try {
                    Color(android.graphics.Color.parseColor(highlight.color))
                } catch (_: Exception) {
                    Color(0xFFFFEB3B)
                }
                val start = highlight.startOffset.coerceIn(0, text.length)
                val end = highlight.endOffset.coerceIn(0, text.length)
                if (start < end) {
                    addStyle(
                        SpanStyle(background = color.copy(alpha = 0.4f)),
                        start,
                        end
                    )
                }
            }

            // TTS sentence highlight — renders behind the text while TTS reads
            if (ttsSentence != null) {
                val sentenceStart = ttsSentence.start.coerceIn(0, text.length)
                val sentenceEnd = ttsSentence.end.coerceIn(0, text.length)
                if (sentenceStart < sentenceEnd) {
                    addStyle(
                        SpanStyle(
                            background = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        sentenceStart,
                        sentenceEnd
                    )
                }
            }
        }
    }

    var longPressTriggered by remember { mutableStateOf(false) }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = (textSettings.fontSize * textSettings.lineSpacingMultiplier).sp,
            fontSize = textSettings.fontSize.sp,
            fontFamily = textSettings.selectedFontFamily.fontFamily
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { offset ->
                        // Calculate character offset from tap position
                        // Approximate: divide x offset by average character width
                        val avgCharWidth = 8f // approximate pixels per character at default font
                        val charOffset = (offset.x / avgCharWidth).toInt().coerceIn(0, text.length)

                        // Select a word around the tapped position
                        val wordStart = text.lastIndexOf(' ', charOffset.coerceAtMost(text.length - 1))
                            .let { if (it == -1) 0 else it + 1 }
                        val wordEnd = text.indexOf(' ', charOffset)
                            .let { if (it == -1) text.length else it }

                        val selectedText = text.substring(wordStart, wordEnd).trim()
                        if (selectedText.isNotEmpty()) {
                            longPressTriggered = true
                            onLongPress(selectedText, wordStart, wordEnd)
                        }
                    },
                    onTap = {
                        longPressTriggered = false
                    }
                )
            }
    )
}

/**
 * Extension to count Highlights by color for display.
 */
fun List<ReaderHighlight>.colorsSummary(): Map<String, Int> {
    return groupingBy { it.color }.eachCount()
}
