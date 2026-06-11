package com.mimiral.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import com.mimiral.app.data.reader.Sentence

/**
 * Renders page text with highlight overlays applied.
 * Supports long press gesture to trigger text selection for highlighting.
 * Also renders TTS sentence-level highlight and word-level highlight when TTS
 * is actively reading.
 *
 * When a word-level range [ttsWordStart, ttsWordEnd) is available, the active
 * word gets a stronger highlight while the containing sentence gets a subtler
 * background. When only sentence-level data is available, the sentence gets
 * the full highlight.
 */
@Composable
fun HighlightableText(
    text: String,
    highlights: List<ReaderHighlight>,
    textSettings: TextSettings,
    ttsSentence: Sentence? = null,
    ttsWordStart: Int = -1,
    ttsWordEnd: Int = -1,
    ttsHighlightColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
    onLongPress: (selectedText: String, startOffset: Int, endOffset: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Build AnnotatedString with highlight spans and TTS highlighting applied
    val annotatedText: androidx.compose.ui.text.AnnotatedString = remember(
        text,
        highlights,
        ttsSentence,
        ttsWordStart,
        ttsWordEnd,
        ttsHighlightColor
    ) {
        buildAnnotatedString {
            append(text)

            // User-created saved highlights
            highlights.forEach { highlight ->
                val color = parseHighlightColor(highlight.color)
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

            val hasWordHighlight = ttsWordStart >= 0 && ttsWordEnd > ttsWordStart

            if (hasWordHighlight && ttsSentence != null) {
                // Word-level highlighting available: sentence gets subtle bg,
                // active word gets stronger highlight
                val sentenceStart = ttsSentence.start.coerceIn(0, text.length)
                val sentenceEnd = ttsSentence.end.coerceIn(0, text.length)
                val wordStart = ttsWordStart.coerceIn(0, text.length)
                val wordEnd = ttsWordEnd.coerceIn(0, text.length)

                if (sentenceStart < sentenceEnd) {
                    // Apply subtle sentence background
                    addStyle(
                        SpanStyle(background = ttsHighlightColor.copy(alpha = 0.3f)),
                        sentenceStart,
                        sentenceEnd
                    )
                }

                if (wordStart < wordEnd) {
                    // Apply stronger word highlight (overwrites the sentence bg in this range)
                    addStyle(
                        SpanStyle(background = ttsHighlightColor),
                        wordStart,
                        wordEnd
                    )
                }
            } else if (ttsSentence != null) {
                // No word-level data: fall back to full sentence highlight
                val sentenceStart = ttsSentence.start.coerceIn(0, text.length)
                val sentenceEnd = ttsSentence.end.coerceIn(0, text.length)
                if (sentenceStart < sentenceEnd) {
                    addStyle(
                        SpanStyle(background = ttsHighlightColor),
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
                        val wordStart = text.lastIndexOf(
                            ' ',
                            charOffset.coerceAtMost(text.length - 1)
                        )
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
