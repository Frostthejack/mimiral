package com.mimiral.app.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.reader.MarkdownElement
import com.mimiral.app.data.reader.TocHeading
import kotlinx.coroutines.launch

/**
 * Markdown reader screen that renders parsed markdown elements with full formatting.
 *
 * Features:
 * - Headings (h1-h6) with decreasing font size
 * - Bold, italic, and inline code spans within paragraphs
 * - Code blocks with monospace font and background
 * - Unordered and ordered lists with proper indentation
 * - Blockquotes with left border accent
 * - Tappable links that open in browser
 * - Table of Contents from headings
 * - Bookmark support
 * - Progress tracking via scroll position
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownReaderScreen(
    bookId: Int,
    onNavigateBack: () -> Unit,
    viewModel: MarkdownReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Load the markdown file when the screen opens
    LaunchedEffect(bookId) {
        val book = viewModel.javaClass.getDeclaredField("bookRepository").let { field ->
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val repo = field.get(viewModel) as? com.mimiral.app.data.repository.BookRepository
            repo?.getBookById(bookId)
        }
        if (book != null) {
            viewModel.loadFile(book.filePath, book.title)
        }
    }

    // Scroll to saved position
    LaunchedEffect(uiState.isLoading, uiState.elements) {
        if (!uiState.isLoading && uiState.elements.isNotEmpty()) {
            val targetIndex = uiState.currentScrollIndex.coerceIn(
                0,
                (uiState.elements.size - 1).coerceAtLeast(0)
            )
            if (targetIndex > 0) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    // Track scroll position
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (!uiState.isLoading && listState.firstVisibleItemIndex >= 0) {
            viewModel.onScrollIndexChanged(listState.firstVisibleItemIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.bookTitle,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.elements.isNotEmpty()) {
                            Text(
                                text = "${uiState.currentScrollIndex + 1}" +
                                    " / ${uiState.elements.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showToc() }) {
                        Icon(Icons.Default.List, contentDescription = "Table of Contents")
                    }
                    IconButton(onClick = { viewModel.toggleBookmark() }) {
                        Icon(
                            imageVector = if (uiState.isCurrentPageBookmarked) {
                                Icons.Default.Bookmark
                            } else {
                                Icons.Default.BookmarkBorder
                            },
                            contentDescription = if (uiState.isCurrentPageBookmarked) {
                                "Remove bookmark"
                            } else {
                                "Add bookmark"
                            },
                            tint = if (uiState.isCurrentPageBookmarked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error ?: "Error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                uiState.elements.isEmpty() -> {
                    Text(
                        text = "No content to display",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                else -> {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentPadding = PaddingValues(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            )
                        ) {
                            itemsIndexed(uiState.elements) { index, element ->
                                MarkdownElementRenderer(
                                    element = element,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            // Bottom spacer for comfortable scrolling
                            item {
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }

            // Bottom progress indicator
            if (!uiState.isLoading && uiState.elements.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = (uiState.progressPercent / 100f).coerceIn(0f, 1f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    trackColor = Color.Transparent
                )
            }
        }
    }

    // Table of Contents dialog
    if (uiState.showToc) {
        MarkdownTocDialog(
            headings = uiState.tocHeadings,
            currentIndex = uiState.currentScrollIndex,
            onNavigateToHeading = { heading ->
                viewModel.navigateToHeading(heading.elementIndex)
                coroutineScope.launch {
                    listState.animateScrollToItem(
                        heading.elementIndex.coerceIn(
                            0,
                            (uiState.elements.size - 1).coerceAtLeast(0)
                        )
                    )
                }
            },
            onDismiss = { viewModel.dismissToc() }
        )
    }

    // Bookmarks dialog
    if (uiState.showBookmarks) {
        BookmarkListDialog(
            bookmarks = uiState.bookmarks,
            onNavigateToBookmark = { bookmark ->
                viewModel.navigateToBookmark(bookmark)
                coroutineScope.launch {
                    listState.animateScrollToItem(
                        bookmark.pageNumber.coerceIn(
                            0,
                            (uiState.elements.size - 1).coerceAtLeast(0)
                        )
                    )
                }
            },
            onDeleteBookmark = { bookmark -> viewModel.deleteBookmark(bookmark) },
            onDismiss = { viewModel.dismissBookmarks() }
        )
    }
}

// ---------------------------------------------------------------------------
// Element renderers
// ---------------------------------------------------------------------------

@Composable
private fun MarkdownElementRenderer(
    element: MarkdownElement,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    when (element) {
        is MarkdownElement.Heading -> {
            val fontSize = when (element.level) {
                1 -> 28.sp
                2 -> 24.sp
                3 -> 20.sp
                4 -> 18.sp
                5 -> 16.sp
                else -> 14.sp
            }
            Spacer(modifier = modifier.padding(top = if (element.level <= 2) 16.dp else 12.dp))
            Text(
                text = element.text,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = fontSize * 1.3f
            )
            if (element.level <= 2) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        is MarkdownElement.Paragraph -> {
            val annotated = parseInlineMarkdown(element.text)
            val uriRegex = Regex("\\[([^]]+)]\\(([^)]+)\\)")
            val linkMatch = uriRegex.find(element.text)

            if (linkMatch != null) {
                val url = linkMatch.groupValues[2]
                val displayText = linkMatch.groupValues[1]
                Column(modifier = modifier) {
                    // Check if paragraph is ONLY a link
                    val trimmed = element.text.trim()
                    if (trimmed == "[$displayText]($url)") {
                        // Standalone link — make it tappable
                        Text(
                            text = displayText,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = TextDecoration.Underline
                            ),
                            modifier = Modifier.clickable {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
                                } catch (_: Exception) { }
                            }
                        )
                    } else {
                        // Paragraph with embedded links — render with inline annotations
                        ClickableLinkText(
                            text = element.text,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = modifier.fillMaxWidth()
                )
            }
        }

        is MarkdownElement.CodeBlock -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                if (element.language != null) {
                    Text(
                        text = element.language,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                SelectionContainer {
                    Text(
                        text = element.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        is MarkdownElement.UnorderedList -> {
            Column(modifier = modifier.padding(start = 8.dp)) {
                element.items.forEach { item ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "  \u2022  ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = parseInlineMarkdownInline(item),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        is MarkdownElement.OrderedList -> {
            Column(modifier = modifier.padding(start = 8.dp)) {
                element.items.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "  ${idx + 1}.  ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = parseInlineMarkdownInline(item),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        is MarkdownElement.Blockquote -> {
            val primaryColor = MaterialTheme.colorScheme.primary
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = primaryColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 4.dp.toPx()
                        )
                    }
                    .padding(start = 12.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = element.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontStyle = FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }

        is MarkdownElement.HorizontalRule -> {
            HorizontalDivider(
                modifier = modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Inline markdown parsing (bold, italic, code, links)
// ---------------------------------------------------------------------------

/**
 * Parse inline markdown within a paragraph and return an [AnnotatedString].
 */
@Composable
private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedStringInlined(text)
}

/**
 * Parse inline markdown into a plain string (for list items where we can't easily
 * use AnnotatedString in Text).
 */
private fun parseInlineMarkdownInline(text: String): String {
    // Strip inline formatting for list items
    return text
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("__(.+?)__"), "$1")
        .replace(Regex("\\*(.+?)\\*"), "$1")
        .replace(Regex("_(.+?)_"), "$1")
        .replace(Regex("`(.+?)`"), "$1")
        .replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1")
}

private fun buildAnnotatedStringInlined(text: String): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            // Bold + italic (***text***)
            val boldItalicMatch = Regex("\\*\\*\\*(.+?)\\*\\*\\*").find(remaining)
            // Bold (**text**)
            val boldMatch = Regex("\\*\\*(.+?)\\*\\*").find(remaining)
            // Italic (*text* or _text_)
            val italicAsteriskMatch = Regex("\\*(.+?)\\*").find(remaining)
            val italicUnderscoreMatch = Regex("\\b_(.+?)_\\b").find(remaining)
            // Inline code (`code`)
            val codeMatch = Regex("`(.+?)`").find(remaining)
            // Link [text](val)
            val linkMatch = Regex("\\[([^]]+)]\\(([^)]+)\\)").find(remaining)

            // Find the earliest match
            val matches = listOfNotNull(
                boldItalicMatch?.let { Triple(it, "boldItalic", it.range.first) },
                boldMatch?.let { Triple(it, "bold", it.range.first) },
                (italicAsteriskMatch ?: italicUnderscoreMatch)?.let {
                    Triple(
                        it,
                        "italic",
                        it.range.first
                    )
                },
                codeMatch?.let { Triple(it, "code", it.range.first) },
                linkMatch?.let { Triple(it, "link", it.range.first) }
            )

            val earliest = matches.minByOrNull { it.third }

            if (earliest != null && earliest.third > 0) {
                // Add plain text before the match
                append(remaining.substring(0, earliest.third))
                remaining = remaining.substring(earliest.third)
                continue
            } else if (earliest != null && earliest.third == 0) {
                when (earliest.second) {
                    "boldItalic" -> {
                        withStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                        ) {
                            append(earliest.first.groupValues[1])
                        }
                        remaining = remaining.substring(earliest.first.value.length)
                    }
                    "bold" -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(earliest.first.groupValues[1])
                        }
                        remaining = remaining.substring(earliest.first.value.length)
                    }
                    "italic" -> {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(earliest.first.groupValues[1])
                        }
                        remaining = remaining.substring(earliest.first.value.length)
                    }
                    "code" -> {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0xFFE0E0E0),
                                fontSize = androidx.compose.ui.text.TextStyle().fontSize * 0.9f
                            )
                        ) {
                            append(earliest.first.groupValues[1])
                        }
                        remaining = remaining.substring(earliest.first.value.length)
                    }
                    "link" -> {
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF1A73E8),
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append(earliest.first.groupValues[1])
                        }
                        remaining = remaining.substring(earliest.first.value.length)
                    }
                }
                continue
            }

            // No more matches — append the rest
            append(remaining)
            break
        }
    }
}

/**
 * Renders text with clickable links.
 */
@Composable
private fun ClickableLinkText(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val linkRegex = Regex("\\[([^]]+)]\\(([^)]+)\\)")
    val matches = linkRegex.findAll(text).toList()

    if (matches.isEmpty()) {
        Text(
            text = buildAnnotatedStringInlined(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        )
        return
    }

    // For simplicity, render the full annotated string.
    // Links are styled but the entire paragraph is not individually clickable per-link.
    // For a production app, use ClickableText with AnnotatedString URL annotations.
    Text(
        text = buildAnnotatedStringInlined(text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

// ---------------------------------------------------------------------------
// TOC dialog
// ---------------------------------------------------------------------------

@Composable
private fun MarkdownTocDialog(
    headings: List<TocHeading>,
    currentIndex: Int,
    onNavigateToHeading: (TocHeading) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Table of Contents") },
        text = {
            if (headings.isEmpty()) {
                Text(
                    text = "No headings found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(headings.size) { idx ->
                        val heading = headings[idx]
                        val isCurrent = heading.elementIndex <= currentIndex &&
                            (
                                idx == headings.size - 1 ||
                                    headings[idx + 1].elementIndex > currentIndex
                                )

                        Text(
                            text = heading.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToHeading(heading) }
                                .padding(
                                    start = ((heading.level - 1) * 16).dp,
                                    top = 8.dp,
                                    bottom = 8.dp
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
