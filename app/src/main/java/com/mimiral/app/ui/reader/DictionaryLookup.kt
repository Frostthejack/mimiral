package com.mimiral.app.ui.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.BreakIterator

/**
 * Data class representing a dictionary definition result.
 */
data class DictionaryResult(
    val word: String,
    val phonetic: String? = null,
    val definitions: List<DefinitionEntry> = emptyList(),
    val sourceUrl: String? = null
)

data class DefinitionEntry(
    val partOfSpeech: String,
    val definition: String,
    val example: String? = null
)

/**
 * Fetches the definition of a word using the free DictionaryAPI (dictionaryapi.dev).
 * Runs on the IO dispatcher.
 */
suspend fun fetchDefinition(word: String): DictionaryResult? {
    return withContext(Dispatchers.IO) {
        try {
            val encodedWord = URLEncoder.encode(word.lowercase().trim(), "UTF-8")
            val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/$encodedWord")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode != 200) {
                connection.disconnect()
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            val entry = jsonArray.getJSONObject(0)
            val wordText = entry.optString("word", word)
            val phonetic = entry.optJSONArray("phonetics")?.let { phonetics ->
                (0 until phonetics.length())
                    .mapNotNull { phonetics.getJSONObject(it).optString("text", null) }
                    .firstOrNull()
            }

            val definitions = mutableListOf<DefinitionEntry>()
            val meanings = entry.optJSONArray("meanings")
            if (meanings != null) {
                for (i in 0 until meanings.length()) {
                    val meaning = meanings.getJSONObject(i)
                    val partOfSpeech = meaning.optString("partOfSpeech", "unknown")
                    val defs = meaning.optJSONArray("definitions")
                    if (defs != null) {
                        for (j in 0 until minOf(defs.length(), 3)) {
                            val def = defs.getJSONObject(j)
                            definitions.add(
                                DefinitionEntry(
                                    partOfSpeech = partOfSpeech,
                                    definition = def.optString("definition", ""),
                                    example = def.optString("example", null)
                                        ?.takeIf { it.isNotBlank() }
                                )
                            )
                        }
                    }
                }
            }

            DictionaryResult(
                word = wordText,
                phonetic = phonetic,
                definitions = definitions.take(5),
                sourceUrl = "https://dictionaryapi.dev/"
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Uses [BreakIterator] to find word boundaries around [offset] in [text].
 * Returns the start and end indices of the word, or null if no word is found.
 */
fun getWordBounds(text: String, offset: Int): Pair<Int, Int>? {
    if (text.isBlank() || offset < 0 || offset > text.length) return null
    val breakIterator = BreakIterator.getWordInstance()
    breakIterator.setText(text)

    val start = breakIterator.preceding(offset).takeIf { it != BreakIterator.DONE }
        ?: return null
    val end = breakIterator.following(offset).takeIf { it != BreakIterator.DONE }
        ?: return null

    val word = text.substring(start, end).trim()
    if (word.isEmpty() || word.all { !it.isLetterOrDigit() }) return null

    return Pair(start, end)
}

/**
 * Extracts the word at the given offset in the text using BreakIterator.
 */
fun getWordAtOffset(text: String, offset: Int): String? {
    val bounds = getWordBounds(text, offset) ?: return null
    return text.substring(bounds.first, bounds.second).trim()
}

/**
 * Opens the given URL in an external browser.
 */
fun openInBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) { }
}

/**
 * Opens Google Translate for the given text.
 */
fun openGoogleTranslate(context: Context, text: String) {
    try {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = "https://translate.google.com/?sl=auto&tl=en&text=$encoded"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) { }
}

/**
 * Opens an external dictionary app via ACTION_SEARCH or falls back to a web dictionary.
 */
fun openExternalDictionary(context: Context, word: String) {
    try {
        // Try ACTION_SEARCH first (some dictionary apps handle this)
        val searchIntent = Intent(Intent.ACTION_SEARCH)
        searchIntent.putExtra("query", word)
        // Try to see if any app handles it; if not, fall back to web
        val pm = context.packageManager
        if (searchIntent.resolveActivity(pm) != null) {
            context.startActivity(searchIntent)
        } else {
            // Fallback: open Merriam-Webster online
            val encoded = URLEncoder.encode(word, "UTF-8")
            val url = "https://www.merriam-webster.com/dictionary/$encoded"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    } catch (_: Exception) { }
}

/**
 * A popup composable that displays a dictionary lookup for a long-pressed word.
 *
 * @param word The word to look up.
 * @param offsetX The x offset for popup positioning.
 * @param offsetY The y offset for popup positioning.
 * @param onDismiss Called when the popup should be dismissed.
 */
@Composable
fun DictionaryPopup(
    word: String,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var result by remember(word) { mutableStateOf<DictionaryResult?>(null) }
    var isLoading by remember(word) { mutableStateOf(true) }
    var hasError by remember(word) { mutableStateOf(false) }

    LaunchedEffect(word) {
        isLoading = true
        hasError = false
        val fetched = fetchDefinition(word)
        if (fetched != null) {
            result = fetched
        } else {
            hasError = true
        }
        isLoading = false
    }

    // Full-screen clickable backdrop to dismiss on tap outside
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .padding(
                        start = offsetX.coerceAtMost(200f).dp,
                        top = offsetY.coerceAtMost(400f).dp
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* consume click to prevent dismiss */ },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Header: word + close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = word,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (result?.phonetic != null) {
                                Text(
                                    text = result!!.phonetic!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        hasError -> {
                            Text(
                                text = "No definition found for \"$word\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            )
                        }
                        result != null -> {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            result!!.definitions.forEachIndexed { index, entry ->
                                if (index > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Text(
                                    text = entry.partOfSpeech.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = entry.definition,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (entry.example != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "\"${entry.example}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Light
                                    )
                                }
                            }
                        }
                    }

                    // Action buttons
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = { openExternalDictionary(context, word) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dictionary")
                        }
                        TextButton(
                            onClick = { openGoogleTranslate(context, word) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Translate")
                        }
                        TextButton(
                            onClick = {
                                openInBrowser(
                                    context,
                                    "https://www.google.com/search?q=define+${
                                        URLEncoder.encode(word, "UTF-8")
                                    }"
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("More")
                        }
                    }
                }
            }
        }
    }
}
