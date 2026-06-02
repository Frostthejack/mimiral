package com.mimiral.app.tts

import org.junit.Test
import org.junit.Assert.*
import java.util.Locale

class SSMLBuilderTest {

    private val builder = SSMLBuilder()

    // --- build: basic structure ---

    @Test
    fun build_empty_returnsSpeakTags() {
        val result = builder.build()
        assertEquals("<speak></speak>", result)
    }

    @Test
    fun build_singleSentence_wrapsInSpeak() {
        val result = builder.clear().addText("Hello world.").build()
        assertEquals("<speak>Hello world.</speak>", result)
    }

    @Test
    fun build_twoSentences_insertsBreak() {
        val result = builder.clear().addText("Hello world. Goodbye world.").build()
        assertTrue(
            "Should contain break between sentences",
            result.contains("<break time='500ms'/>")
        )
    }

    @Test
    fun build_twoSentences_breakNotAfterLast() {
        val result = builder.clear().addText("First. Second.").build()
        val breakCount = result.split("<break").size - 1
        assertEquals("Should have exactly 1 break for 2 sentences", 1, breakCount)
    }

    @Test
    fun build_threeSentences_twoBreaks() {
        val result = builder.clear().addText("One. Two. Three.").build()
        val breakCount = result.split("<break").size - 1
        assertEquals("Should have exactly 2 breaks for 3 sentences", 2, breakCount)
    }

    @Test
    fun build_preservesSentenceOrder() {
        val result = builder.clear().addText("First. Second. Third.").build()
        val firstIdx = result.indexOf("First.")
        val secondIdx = result.indexOf("Second.")
        val thirdIdx = result.indexOf("Third.")
        assertTrue("First should come before Second", firstIdx < secondIdx)
        assertTrue("Second should come before Third", secondIdx < thirdIdx)
    }

    // --- addText: sentence splitting ---

    @Test
    fun addText_onPeriod_splitsSentences() {
        val result = builder.clear().addText("Hello. World.").build()
        assertTrue(result.contains("Hello."))
        assertTrue(result.contains("World."))
        assertTrue(result.contains("<break time="))
    }

    @Test
    fun addText_onExclamation_splitsSentences() {
        val result = builder.clear().addText("Hello! World.").build()
        assertTrue(result.contains("Hello!"))
        assertTrue(result.contains("World."))
    }

    @Test
    fun addText_onQuestionMark_splitsSentences() {
        val result = builder.clear().addText("Hello? World.").build()
        assertTrue(result.contains("Hello?"))
        assertTrue(result.contains("World."))
    }

    @Test
    fun addText_blankText_noSentences() {
        val result = builder.clear().addText("").build()
        assertEquals("<speak></speak>", result)
    }

    @Test
    fun addText_whitespaceOnly_noSentences() {
        val result = builder.clear().addText("   ").build()
        assertEquals("<speak></speak>", result)
    }

    // --- addSentences ---

    @Test
    fun addSentences_varargs_addsAll() {
        val result = builder.clear()
            .addSentences("First.", "Second.", "Third.")
            .build()
        assertEquals(3, builder.sentenceCount())
        assertTrue(result.contains("First."))
        assertTrue(result.contains("Second."))
        assertTrue(result.contains("Third."))
    }

    // --- break duration ---

    @Test
    fun build_customBreakDuration() {
        val result = builder.clear()
            .setBreakDuration(750)
            .addText("Hello. World.")
            .build()
        assertTrue(
            "Should contain custom break duration",
            result.contains("<break time='750ms'/>")
        )
    }

    @Test
    fun build_zeroBreakDuration() {
        val result = builder.clear()
            .setBreakDuration(0)
            .addText("Hello. World.")
            .build()
        assertTrue(
            "Should contain zero break duration",
            result.contains("<break time='0ms'/>")
        )
    }

    @Test
    fun setBreakDuration_clampsNegativeToZero() {
        val result = builder.clear()
            .setBreakDuration(-100)
            .addText("Hello. World.")
            .build()
        assertTrue(
            "Negative break should be clamped to 0",
            result.contains("<break time='0ms'/>")
        )
    }

    @Test
    fun setBreakDuration_clampsMaxTo10000() {
        val result = builder.clear()
            .setBreakDuration(20000)
            .addText("Hello. World.")
            .build()
        assertTrue(
            "Excessive break should be clamped to 10000ms",
            result.contains("<break time='10000ms'/>")
        )
    }

    // --- clear / sentenceCount ---

    @Test
    fun clear_removesAllSentences() {
        builder.clear().addText("Hello. World.")
        assertEquals(2, builder.sentenceCount())
        builder.clear()
        assertEquals(0, builder.sentenceCount())
        assertEquals("<speak></speak>", builder.build())
    }

    @Test
    fun sentenceCount_empty_returnsZero() {
        assertEquals(0, builder.clear().sentenceCount())
    }

    // --- applySay-as: well-formed XML ---

    @Test
    fun build_outputIsValidXmlStructure() {
        val result = builder.clear()
            .addText("Hello world. The price is $42.50.")
            .build()
        assertTrue("Should start with <speak>", result.startsWith("<speak>"))
        assertTrue("Should end with </speak>", result.endsWith("</speak>"))
        // Count opening and closing tags match
        val speakOpen = result.split("<speak>").size - 1
        val speakClose = result.split("</speak>").size - 1
        assertEquals("Speak tags should match", speakOpen, speakClose)
    }

    @Test
    fun build_singleSentence_noBreak() {
        val result = builder.clear().addText("Just one sentence.").build()
        assertFalse(
            "Single sentence should not have a break",
            result.contains("<break")
        )
    }

    // --- setLocale ---

    @Test
    fun setLocale_updatesLocale() {
        builder.clear().setLocale(Locale.FRENCH)
        // Just verify no crash — locale is stored for future use
        val result = builder.addText("Bonjour.").build()
        assertEquals("<speak>Bonjour.</speak>", result)
    }

    // --- Special characters: SSML should still be well-formed ---

    @Test
    fun applySayAs_ampersand_escaped() {
        val result = builder.clear()
            .addText("Tom & Jerry.")
            .build()
        assertTrue(
            "Ampersand should be escaped to &amp;",
            result.contains("&amp;")
        )
        assertFalse(
            "Raw ampersand should not appear in output",
            result.contains("Tom & Jerry")
        )
    }

    @Test
    fun applySayAs_lessThan_escaped() {
        val result = builder.clear()
            .addText("5 < 10.")
            .build()
        assertTrue(
            "< should be escaped to &lt;",
            result.contains("&lt;")
        )
    }

    @Test
    fun applySayAs_greaterThan_escaped() {
        val result = builder.clear()
            .addText("10 > 5.")
            .build()
        assertTrue(
            "> should be escaped to &gt;",
            result.contains("&gt;")
        )
    }

    @Test
    fun applySayAs_quotes_escaped() {
        val result = builder.clear()
            .addText("He said \"hello\".")
            .build()
        assertTrue("Double quotes should be escaped", result.contains("&quot;"))
    }

    // --- Date detection for <say-as> ---

    @Test
    fun applySayAs_monthDayYear_detected() {
        val result = builder.clear()
            .addText("Today is January 5, 2026.")
            .build()
        assertTrue(
            "Month-day-date should be wrapped in say-as date",
            result.contains("interpret-as='date'")
        )
        assertTrue("Should contain mdy format", result.contains("format='mdy'"))
    }

    @Test
    fun applySayAs_abbreviatedMonth_detected() {
        val result = builder.clear()
            .addText("The date is Jan 5, 2026.")
            .build()
        assertTrue(
            "Abbreviated month date should be wrapped",
            result.contains("interpret-as='date'")
        )
    }

    @Test
    fun applySayAs_isoDate_detected() {
        val result = builder.clear()
            .addText("Today is 2026-01-05.")
            .build()
        assertTrue(
            "ISO date should be wrapped",
            result.contains("interpret-as='date'")
        )
        assertTrue("Should contain ymd format", result.contains("format='ymd'"))
    }

    @Test
    fun applySayAs_slashDate_detected() {
        val result = builder.clear()
            .addText("Today is 01/05/2026.")
            .build()
        assertTrue(
            "Slash date should be wrapped",
            result.contains("interpret-as='date'")
        )
    }

    // --- Number detection ---

    @Test
    fun applySayAs_largeNumber_detected() {
        val result = builder.clear()
            .addText("The population is 1000000.")
            .build()
        assertTrue(
            "Large number should be wrapped in say-as cardinal",
            result.contains("interpret-as='cardinal'")
        )
    }

    @Test
    fun applySayAs_numberWithCommas_detected() {
        val result = builder.clear()
            .addText("The value is 1,000,000.")
            .build()
        assertTrue(
            "Number with commas should be wrapped",
            result.contains("interpret-as='cardinal'")
        )
    }

    @Test
    fun applySayAs_decimal_detected() {
        val result = builder.clear()
            .addText("The value is 3.14.")
            .build()
        assertTrue(
            "Decimal should be wrapped",
            result.contains("interpret-as='cardinal'")
        )
    }

    // --- Currency detection ---

    @Test
    fun applySayAs_dollarAmount_detected() {
        val result = builder.clear()
            .addText("The price is $42.50.")
            .build()
        assertTrue(
            "Dollar amount should be wrapped in say-as unit",
            result.contains("interpret-as='unit'")
        )
    }

    // --- Ordinal detection ---

    @Test
    fun applySayAs_ordinal_detected() {
        val result = builder.clear()
            .addText("He finished 1st.")
            .build()
        assertTrue(
            "Ordinal should be wrapped",
            result.contains("interpret-as='ordinal'")
        )
    }

    // --- Integration: full SSML output ---

    @Test
    fun build_fullSsml_twoSentencesWithBreaksAndMarkup() {
        val result = builder.clear()
            .addText(
                "Hello world. Today is January 5, 2026. The price is $42.50."
            )
            .build()
        // Well-formed
        assertTrue("Starts with <speak>", result.startsWith("<speak>"))
        assertTrue("Ends with </speak>", result.endsWith("</speak>"))
        // Has breaks between 3 sentences = 2 breaks
        val breakCount = result.split("<break").size - 1
        assertEquals(2, breakCount)
        // Has date markup
        assertTrue("Has date markup", result.contains("interpret-as='date'"))
        // Has currency markup
        assertTrue("Has currency markup", result.contains("interpret-as='unit'"))
    }

    @Test
    fun build_ssmlAllSentencesHaveBreaksBetween() {
        val result = builder.clear()
            .setBreakDuration(300)
            .addText("One. Two. Three. Four.")
            .build()
        val breakCount = result.split("<break").size - 1
        assertEquals("4 sentences should have 3 breaks", 3, breakCount)
        assertTrue("All breaks should be 300ms", result.contains("300ms"))
    }

    @Test
    fun build_chaining_returnsSameBuilder() {
        val b = builder.clear()
        val result = b
            .addText("Hello.")
            .setBreakDuration(400)
            .addText("World.")
            .build()
        assertTrue(result.contains("400ms"))
    }
}
