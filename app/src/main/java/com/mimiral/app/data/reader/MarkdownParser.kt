package com.mimiral.app.data.reader

import java.io.File

/**
 * Lightweight Markdown parser that converts .md files into structured [MarkdownElement]s.
 *
 * Supports:
 * - Headings (h1-h6 via # syntax)
 * - Bold (**text** or __text__)
 * - Italic (*text* or _text_)
 * - Links ([text](url))
 * - Unordered lists (-, *, +)
 * - Ordered lists (1. 2. 3.)
 * - Code blocks (fenced with ``` or indented)
 * - Inline code (`code`)
 * - Blockquotes (> text)
 * - Horizontal rules (---, ***, ___)
 * - Plain paragraphs
 *
 * No external dependencies — uses line-by-line scanning with a simple state machine.
 */
class MarkdownParser {

    /**
     * Parse a markdown file into a list of [MarkdownElement]s.
     */
    fun parse(file: File): List<MarkdownElement> {
        require(file.exists()) { "File not found: ${file.absolutePath}" }
        val lines = file.readLines()
        return parseLines(lines)
    }

    /**
     * Parse markdown text into a list of [MarkdownElement]s.
     */
    fun parseText(text: String): List<MarkdownElement> {
        return parseLines(text.lines())
    }

    private fun parseLines(lines: List<String>): List<MarkdownElement> {
        val elements = mutableListOf<MarkdownElement>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            // Blank line — skip
            if (line.isBlank()) {
                i++
                continue
            }

            // Fenced code block (``` or ~~~)
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                val fence = line.trimStart().take(3)
                val lang = line.trimStart().substring(3).trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(fence)) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // skip closing fence
                elements.add(
                    MarkdownElement.CodeBlock(
                        code = codeLines.joinToString("\n"),
                        language = lang.ifEmpty { null }
                    )
                )
                continue
            }

            // Heading
            val headingMatch = headingRegex.find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2].trim()
                elements.add(MarkdownElement.Heading(level = level, text = text))
                i++
                continue
            }

            // Horizontal rule
            if (hrRegex.matches(line.trim())) {
                elements.add(MarkdownElement.HorizontalRule)
                i++
                continue
            }

            // Blockquote
            if (line.trimStart().startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quoteLines.add(lines[i].trimStart().removePrefix(">").trimStart())
                    i++
                }
                elements.add(MarkdownElement.Blockquote(quoteLines.joinToString("\n")))
                continue
            }

            // Unordered list
            if (ulStartRegex.containsMatchIn(line)) {
                val items = mutableListOf<String>()
                while (i < lines.size && (ulStartRegex.containsMatchIn(lines[i]) || (lines[i].isBlank().not() && lines[i].startsWith("  ")))) {
                    val itemLine = lines[i]
                    if (ulStartRegex.containsMatchIn(itemLine)) {
                        items.add(itemLine.replace(ulStartRegex, "").trim())
                    } else {
                        // Continuation line
                        if (items.isNotEmpty()) {
                            items[items.lastIndex] = items.last() + "\n" + itemLine.trim()
                        }
                    }
                    i++
                }
                elements.add(MarkdownElement.UnorderedList(items))
                continue
            }

            // Ordered list
            if (olStartRegex.containsMatchIn(line)) {
                val items = mutableListOf<String>()
                while (i < lines.size && (olStartRegex.containsMatchIn(lines[i]) || (lines[i].isBlank().not() && lines[i].startsWith("  ")))) {
                    val itemLine = lines[i]
                    if (olStartRegex.containsMatchIn(itemLine)) {
                        items.add(itemLine.replace(olStartRegex, "").trim())
                    } else {
                        if (items.isNotEmpty()) {
                            items[items.lastIndex] = items.last() + "\n" + itemLine.trim()
                        }
                    }
                    i++
                }
                elements.add(MarkdownElement.OrderedList(items))
                continue
            }

            // Paragraph (collect until blank line or new block element)
            val paraLines = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank() &&
                !lines[i].trimStart().startsWith("#") &&
                !lines[i].trimStart().startsWith("```") &&
                !lines[i].trimStart().startsWith("~~~") &&
                !lines[i].trimStart().startsWith(">") &&
                !lines[i].trimStart().startsWith("- ") &&
                !lines[i].trimStart().startsWith("* ") &&
                !lines[i].trimStart().startsWith("+ ") &&
                !olStartRegex.containsMatchIn(lines[i]) &&
                !hrRegex.matches(lines[i].trim())
            ) {
                paraLines.add(lines[i])
                i++
            }
            if (paraLines.isNotEmpty()) {
                elements.add(MarkdownElement.Paragraph(paraLines.joinToString(" ")))
            }
        }

        return elements
    }

    /**
     * Extract headings from parsed elements for TOC generation.
     */
    fun extractTableOfContents(elements: List<MarkdownElement>): List<TocHeading> {
        val headings = mutableListOf<TocHeading>()
        for ((index, element) in elements.withIndex()) {
            if (element is MarkdownElement.Heading) {
                headings.add(
                    TocHeading(
                        level = element.level,
                        title = element.text,
                        elementIndex = index
                    )
                )
            }
        }
        return headings
    }

    companion object {
        private val headingRegex = Regex("^(#{1,6})\\s+(.+)$")
        private val hrRegex = Regex("^([-*_])\\1{2,}$")
        private val ulStartRegex = Regex("^(\\s*)[-*+]\\s+")
        private val olStartRegex = Regex("^(\\s*)\\d+\\.\\s+")
    }
}

/**
 * Represents a parsed markdown element.
 */
sealed class MarkdownElement {
    data class Heading(val level: Int, val text: String) : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()
    data class CodeBlock(val code: String, val language: String?) : MarkdownElement()
    data class UnorderedList(val items: List<String>) : MarkdownElement()
    data class OrderedList(val items: List<String>) : MarkdownElement()
    data class Blockquote(val text: String) : MarkdownElement()
    object HorizontalRule : MarkdownElement()
}

/**
 * A heading entry for the table of contents.
 */
data class TocHeading(
    val level: Int,
    val title: String,
    val elementIndex: Int
)
