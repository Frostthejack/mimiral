package com.mimiral.app.tts

import java.util.Locale
import java.util.regex.Pattern

/**
 * Builder for generating SSML (Speech Synthesis Markup Language) output
 * compatible with Android's [android.speech.tts.TextToSpeech] engine.
 *
 * Produces a minimal SSML subset:
 * - `<speak>` root element
 * - `<break>` tags between sentences for natural pauses
 * - `<say-as>` for dates, numbers, and abbreviations
 *
 * Example usage:
 * ```
 * val ssml = SSMLBuilder()
 *     .addText("Hello world.")
 *     .addText("Today is January 5, 2026.")
 *     .addText("The price is $42.50.")
 *     .build()
 * // TTS engine receives: <speak>Hello world.<break time='500ms'/>Today is <say-as interpret-as='date' format='mdy'>January 5, 2026</say-as>.<break time='500ms'/>The price is <say-as interpret-as='cardinal'>42.50</say-as> dollars.</speak>
 * ```
 *
 * @see <a href="https://www.w3.org/TR/speech-synthesis11/">W3C SSML 1.1 Specification</a>
 */
class SSMLBuilder {

    companion object {
        /** Default pause duration between sentences. */
        const val DEFAULT_BREAK_MS = 500

        /** Namespace used by Android TTS for SSML. */
        const val SSML_NAMESPACE = "http://www.w3.org/2001/10/synthesis"

        // --- Date patterns ---
        // Matches "January 5, 2026" or "Jan 5, 2026"
        private val DATE_MONTH_DAY_YEAR = Pattern.compile(
            "(January|February|March|April|May|June|July|August|September|October|November|December|" +
                "Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})"
        )

        // Matches "5 January 2026" or "5 Jan 2026" (non-US format)
        private val DAY_MONTH_YEAR = Pattern.compile(
            "(\\d{1,2})\\s+(January|February|March|April|May|June|July|August|September|October|November|December|" +
                "Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\.?,?\\s+(\\d{4})"
        )

        // Matches "2026-01-05" (ISO date)
        private val DATE_ISO = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})")

        // Matches "01/05/2026" or "1/5/2026" (US date)
        private val DATE_SLASH = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})")

        // --- Number patterns ---
        // Matches large numbers with commas: 1,000,000
        private val NUMBER_WITH_COMMAS = Pattern.compile("(\\d{1,3}(,\\d{3})+)(\\.\\d+)?")

        // Matches decimal numbers: 3.14, 42.50
        private val DECIMAL_NUMBER = Pattern.compile("(?<!\\d)(\\d+\\.\\d+)(?!\\d)")

        // Matches integers (3+ digits that aren't part of a decimal): 42, 100, 1000
        private val INTEGER_NUMBER = Pattern.compile("(?<!\\.)(\\d{3,})(?!\\.\\d)")

        // Matches standalone 1-2 digit numbers that aren't ordinals
        private val SMALL_INTEGER = Pattern.compile("(?<![\\d.])(\\d{1,2})(?!\\d)(?!st|nd|rd|th)")

        // --- Currency ---
        // Matches dollar amounts: $42.50, $1,000
        private val CURRENCY_DOLLAR = Pattern.compile("\\$\\s*([\\d,]+(\\.\\d{1,2})?)")

        // Matches euro amounts: €42.50
        private val CURRENCY_EURO = Pattern.compile("€\\s*([\\d,]+(\\.\\d{1,2})?)")

        // --- Ordinals ---
        // Matches ordinal numbers: 1st, 2nd, 3rd, 4th, 21st, etc.
        private val ORDINAL = Pattern.compile("(\\d+)(st|nd|rd|th)")

        // --- Abbreviation patterns for <say-as> ---
        // Matches time patterns like "3:30 PM" or "15:30"
        private val TIME = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(AM|PM|am|pm)?")

        // Matches percentage: 42%
        private val PERCENTAGE = Pattern.compile("(\\d+(\\.\\d+)?)\\s*%")
    }

    private val sentences = mutableListOf<String>()
    private var breakDurationMs = DEFAULT_BREAK_MS
    private var locale: Locale = Locale.getDefault()

    /**
     * Adds a block of text that will be treated as one or more sentences.
     * Sentences are split on sentence-ending punctuation (.!?).
     */
    fun addText(text: String): SSMLBuilder {
        if (text.isBlank()) return this
        // Split on sentence boundaries but keep the punctuation
        val parts = text.split("(?<=[.!?])\\s+".toRegex())
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) {
                sentences.add(trimmed)
            }
        }
        return this
    }

    /**
     * Adds pre-split sentences.
     */
    fun addSentences(vararg sents: String): SSMLBuilder {
        for (s in sents) {
            val trimmed = s.trim()
            if (trimmed.isNotEmpty()) {
                sentences.add(trimmed)
            }
        }
        return this
    }

    /**
     * Sets the break duration between sentences in milliseconds.
     */
    fun setBreakDuration(ms: Int): SSMLBuilder {
        breakDurationMs = ms.coerceIn(0, 10000)
        return this
    }

    /**
     * Sets the locale for locale-aware processing (date formats, etc.)
     */
    fun setLocale(loc: Locale): SSMLBuilder {
        locale = loc
        return this
    }

    /**
     * Builds the complete SSML document string.
     *
     * @return SSML string wrapped in <speak> tags, ready to pass to TextToSpeech.
     */
    fun build(): String {
        if (sentences.isEmpty()) return "<speak></speak>"

        val sb = StringBuilder()
        sb.append("<speak>")

        for ((index, sentence) in sentences.withIndex()) {
            val processed = applySayAs(escapeXml(sentence))
            sb.append(processed)
            // Add break between sentences, but not after the last one
            if (index < sentences.size - 1) {
                sb.append("<break time='${breakDurationMs}ms'/>")
            }
        }

        sb.append("</speak>")
        return sb.toString()
    }

    /**
     * Returns the number of sentences currently in the builder.
     */
    fun sentenceCount(): Int = sentences.size

    /**
     * Clears all accumulated sentences.
     */
    fun clear(): SSMLBuilder {
        sentences.clear()
        return this
    }

    // -------------------------------------------------------------------------
    // Internal processing
    // -------------------------------------------------------------------------

    /**
     * Applies <say-as> markup for dates, numbers, currencies, ordinals,
     * times, and percentages.
     *
     * Processing order matters:
     * 1. Currency ($ / €)  before numbers to avoid double-wrapping
     * 2. Time before numbers (time contains colons that could confuse parsing)
     * 3. Ordinals (1st, 2nd) before plain integers
     * 4. Percentage before plain integers
     * 5. Dates (month-day-year, ISO, slash) before plain numbers
     * 6. Numbers with commas (1,000) before plain integers
     * 7. Decimals before integers
     * 8. Plain integers last
     */
    internal fun applySayAs(text: String): String {
        var result = text

        // 1. Currency
        result = wrapCurrency(result)

        // 2. Time
        result = wrapTime(result)

        // 3. Ordinals
        result = wrapOrdinals(result)

        // 4. Percentage
        result = wrapPercentage(result)

        // 5. Dates
        result = wrapDates(result)

        // 6. Numbers
        result = wrapNumbers(result)

        return result
    }

    /**
     * Escapes XML special characters that would break SSML well-formedness.
     */
    internal fun escapeXml(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    // --- Date wrapping ---

    private fun wrapDates(text: String): String {
        var result = text
        // Month Day Year: "January 5, 2026" -> <say-as interpret-as="date" format="mdy">January 5, 2026</say-as>
        result = DATE_MONTH_DAY_YEAR.matcher(result).replaceAll(
            "<say-as interpret-as='date' format='mdy'>$0</say-as>"
        )
        // ISO dates: "2026-01-05" -> <say-as interpret-as="date" format="ymd">2026-01-05</say-as>
        result = DATE_ISO.matcher(result).replaceAll(
            "<say-as interpret-as='date' format='ymd'>$1-$2-$3</say-as>"
        )
        // Slash dates: "01/05/2026" -> <say-as interpret-as="date" format="mdy">01/05/2026</say-as>
        result = DATE_SLASH.matcher(result).replaceAll(
            "<say-as interpret-as='date' format='mdy'>$1/$2/$3</say-as>"
        )
        return result
    }

    // --- Number wrapping ---

    private fun wrapNumbers(text: String): String {
        var result = text
        // Numbers with commas: "1,000,000" -> <say-as interpret-as="cardinal">1,000,000</say-as>
        result = NUMBER_WITH_COMMAS.matcher(result).replaceAll(
            "<say-as interpret-as='cardinal'>$0</say-as>"
        )
        // Decimal numbers: "3.14" -> <say-as interpret-as="cardinal">3.14</say-as>
        result = DECIMAL_NUMBER.matcher(result).replaceAll(
            "<say-as interpret-as='cardinal'>$1</say-as>"
        )
        // Large integers (3+ digits): "1000" -> <say-as interpret-as="cardinal">1000</say-as>
        result = INTEGER_NUMBER.matcher(result).replaceAll(
            "<say-as interpret-as='cardinal'>$1</say-as>"
        )
        // Small integers (1-2 digits): "42" -> <say-as interpret-as="cardinal">42</say-as>
        result = SMALL_INTEGER.matcher(result).replaceAll(
            "<say-as interpret-as='cardinal'>$1</say-as>"
        )
        return result
    }

    private fun wrapCurrency(text: String): String {
        var result = text
        // Dollar
        result = CURRENCY_DOLLAR.matcher(result).replaceAll(
            "<say-as interpret-as='unit'>dollars $1</say-as>"
        )
        // Euro
        result = CURRENCY_EURO.matcher(result).replaceAll(
            "<say-as interpret-as='unit'>euros $1</say-as>"
        )
        return result
    }

    private fun wrapOrdinals(text: String): String {
        return ORDINAL.matcher(text).replaceAll("<say-as interpret-as='ordinal'>$1$2</say-as>")
    }

    private fun wrapTime(text: String): String {
        return TIME.matcher(text).replaceAll(
            "<say-as interpret-as='time' format='hms12'>$1:$2 $3</say-as>"
        )
    }

    private fun wrapPercentage(text: String): String {
        return PERCENTAGE.matcher(text).replaceAll(
            "<say-as interpret-as='percent'>$1 percent</say-as>"
        )
    }
}
