package com.mimiral.app.tts

import org.junit.Test
import org.junit.Assert.*

class TTSPreprocessorTest {

    private val preprocessor = TTSPreprocessor()

    // --- preprocess: general ---

    @Test
    fun preprocess_emptyString_returnsEmpty() {
        assertEquals("", preprocessor.preprocess(""))
    }

    @Test
    fun preprocess_blankString_returnsBlank() {
        assertEquals("   ", preprocessor.preprocess("   "))
    }

    @Test
    fun preprocess_plainText_unchanged() {
        val text = "Hello world. This is a normal sentence."
        assertEquals(text, preprocessor.preprocess(text))
    }

    // --- Em-dash handling ---

    @Test
    fun preprocess_emDash_replacedWithPause() {
        val result = preprocessor.preprocess("Hello\u2014world")
        assertTrue("Em-dash should be replaced with pause", result.contains(", "))
        assertFalse("Em-dash should not remain in output", result.contains("\u2014"))
    }

    @Test
    fun preprocess_emDashBetweenWords_insertsPause() {
        val result = preprocessor.preprocess("She paused\u2014then continued.")
        assertEquals("She paused, then continued.", result)
    }

    @Test
    fun preprocess_emDashAtStart_producesPause() {
        val result = preprocessor.preprocess("\u2014And so it began.")
        assertTrue(result.startsWith(", "))
    }

    // --- En-dash handling ---

    @Test
    fun preprocess_enDash_replacedWithPause() {
        val result = preprocessor.preprocess("Pages 10\u201320")
        assertTrue("En-dash should be replaced with pause", result.contains(", "))
        assertFalse("En-dash should not remain in output", result.contains("\u2013"))
    }

    // --- Smart quote handling ---

    @Test
    fun preprocess_smartSingleQuotes_replacedWithStraight() {
        val input = "\u2018Hello\u2019 he said"
        val result = preprocessor.preprocess(input)
        assertTrue("Should contain straight quote", result.contains("'"))
        assertFalse("Left smart quote should not remain", result.contains("\u2018"))
        assertFalse("Right smart quote should not remain", result.contains("\u2019"))
    }

    @Test
    fun preprocess_smartDoubleQuotes_replacedWithStraight() {
        val input = "\u201cHello\u201d she said"
        val result = preprocessor.preprocess(input)
        assertTrue("Should contain straight double quote", result.contains("\""))
        assertFalse("Left smart double quote should not remain", result.contains("\u201c"))
        assertFalse("Right smart double quote should not remain", result.contains("\u201d"))
    }

    @Test
    fun preprocess_mixedSmartQuotes_allReplaced() {
        val input = "\u2018Hello\u2019 \u201cWorld\u201d"
        val result = preprocessor.preprocess(input)
        assertFalse(result.contains("\u2018"))
        assertFalse(result.contains("\u2019"))
        assertFalse(result.contains("\u201c"))
        assertFalse(result.contains("\u201d"))
    }

    @Test
    fun preprocess_angleQuotes_replacedWithStraight() {
        val input = "\u00abBonjour\u00bb"
        val result = preprocessor.preprocess(input)
        assertTrue("Should contain straight double quote", result.contains("\""))
        assertFalse(result.contains("\u00ab"))
        assertFalse(result.contains("\u00bb"))
    }

    // --- Ligature handling ---

    @Test
    fun preprocess_ligatureAE_expanded() {
        val result = preprocessor.preprocess("Encyclop\u00e6dia")
        assertTrue("ae ligature should be expanded to 'ae'", result.contains("ae"))
        assertFalse("ae ligature char should not remain", result.contains("\u00e6"))
    }

    @Test
    fun preprocess_ligature_oe_expanded() {
        val result = preprocessor.preprocess("\u0153uvre")
        assertTrue("oe ligature should be expanded to 'oe'", result.contains("oe"))
        assertFalse("oe ligature char should not remain", result.contains("\u0153"))
    }

    @Test
    fun preprocess_ligature_sz_expanded() {
        val result = preprocessor.preprocess("Stra\u00dfe")
        assertTrue("sz ligature should be expanded to 'ss'", result.contains("ss"))
        assertFalse("sz ligature char should not remain", result.contains("\u00df"))
    }

    @Test
    fun preprocess_ligature_fi_expanded() {
        // fi ligature is a Unicode char, test it directly
        val result = preprocessor.preprocess("first\uFB01nd")
        assertTrue("fi ligature should be expanded", result.contains("fi"))
    }

    @Test
    fun preprocess_ligature_fl_expanded() {
        val result = preprocessor.preprocess("\uFB02ower")
        assertTrue("fl ligature should be expanded", result.contains("fl"))
    }

    // --- Abbreviation handling ---

    @Test
    fun preprocess_abbreviationMr_expanded() {
        val result = preprocessor.preprocess("Mr. Smith arrived.")
        assertTrue("Mr. should be expanded to Mister", result.contains("Mister"))
    }

    @Test
    fun preprocess_abbreviationDr_expanded() {
        val result = preprocessor.preprocess("Dr. Jones is here.")
        assertTrue("Dr. should be expanded to Doctor", result.contains("Doctor"))
    }

    @Test
    fun preprocess_abbreviationMrs_expanded() {
        val result = preprocessor.preprocess("Mrs. Brown attended.")
        assertTrue("Mrs. should be expanded to Missus", result.contains("Missus"))
    }

    @Test
    fun preprocess_abbreviationEtc_expanded() {
        val result = preprocessor.preprocess("Items like pens, paper, etc.")
        assertTrue("etc should be expanded to etcetera", result.contains("etcetera"))
    }

    @Test
    fun preprocess_abbreviationEg_expanded() {
        val result = preprocessor.preprocess("Use colors e.g. red or blue.")
        assertTrue("e.g. should be expanded to 'for example'", result.contains("for example"))
    }

    @Test
    fun preprocess_abbreviationIe_expanded() {
        val result = preprocessor.preprocess("Namely, i.e. that one.")
        assertTrue("i.e. should be expanded to 'that is'", result.contains("that is"))
    }

    @Test
    fun preprocess_abbreviationMidword_notExpanded() {
        // "org" inside "organize" should NOT be expanded
        val result = preprocessor.preprocess("We need to organize this.")
        assertFalse("Mid-word 'org' should not be expanded", result.contains("organization"))
    }

    @Test
    fun preprocess_abbreviationCaseInsensitive() {
        val result = preprocessor.preprocess("MR. Smith arrived.")
        assertTrue("MR. should be expanded (case insensitive)", result.contains("Mister"))
    }

    // --- Other special characters ---

    @Test
    fun preprocess_ellipsis_replacedWithPause() {
        val result = preprocessor.preprocess("Well\u2026 I don't know")
        assertTrue("Ellipsis should be replaced with pause", result.contains(", "))
        assertFalse("Ellipsis char should not remain", result.contains("\u2026"))
    }

    @Test
    fun preprocess_bullet_replacedWithSpace() {
        val result = preprocessor.preprocess("\u2022 Item one")
        assertTrue("Bullet should be replaced with space", result.contains("Item one"))
        assertFalse("Bullet char should not remain", result.contains("\u2022"))
        assertFalse("No leading whitespace after normalize", result.startsWith(" "))
    }

    @Test
    fun preprocess_minus_replacedWithWord() {
        val result = preprocessor.preprocess("5 \u2212 3 = 2")
        assertTrue("Minus sign should be replaced with 'minus'", result.contains("minus"))
    }

    @Test
    fun preprocess_times_replacedWithWord() {
        val result = preprocessor.preprocess("3 \u00d7 4 = 12")
        assertTrue("Times sign should be replaced with 'times'", result.contains("times"))
    }

    @Test
    fun preprocess_division_replacedWithWord() {
        val result = preprocessor.preprocess("10 \u00f7 2 = 5")
        assertTrue("Division sign should be replaced with 'divided by'", result.contains("divided by"))
    }

    // --- ReplaceCharacters method ---

    @Test
    fun replaceCharacters_noSpecialChars_unchanged() {
        val text = "Hello world!"
        assertEquals(text, preprocessor.replaceCharacters(text))
    }

    @Test
    fun replaceCharacters_emDashToPause() {
        assertEquals("Hello, world", preprocessor.replaceCharacters("Hello\u2014world"))
    }

    @Test
    fun replaceCharacters_smartQuotesToStraight() {
        assertEquals(
            "'Hello' \"world\"",
            preprocessor.replaceCharacters("\u2018Hello\u2019 \u201cworld\u201d")
        )
    }

    // --- ExpandAbbreviations method ---

    @Test
    fun expandAbbreviations_noAbbreviations_unchanged() {
        val text = "Hello world. This is normal text."
        assertEquals(text, preprocessor.expandAbbreviations(text))
    }

    @Test
    fun expandAbbreviations_DrAtStart() {
        assertEquals(
            "Doctor Smith is here.",
            preprocessor.expandAbbreviations("Dr. Smith is here.")
        )
    }

    @Test
    fun expandAbbreviations_MultipleAbbreviations() {
        val result = preprocessor.expandAbbreviations("Mr. and Dr. Jones arrived.")
        assertTrue(result.contains("Mister"))
        assertTrue(result.contains("Doctor"))
    }

    // --- Utility methods ---

    @Test
    fun containsSpecialCharacters_withEmDash_returnsTrue() {
        assertTrue(preprocessor.containsSpecialCharacters("Hello\u2014world"))
    }

    @Test
    fun containsSpecialCharacters_withoutSpecialChars_returnsFalse() {
        assertFalse(preprocessor.containsSpecialCharacters("Hello world"))
    }

    @Test
    fun containsSpecialCharacters_withSmartQuote_returnsTrue() {
        assertTrue(preprocessor.containsSpecialCharacters("\u2018Hello\u2019"))
    }

    @Test
    fun containsSpecialCharacters_withLigature_returnsTrue() {
        assertTrue(preprocessor.containsSpecialCharacters("caf\u00e9\u00e6"))
    }

    @Test
    fun getHandledCharacters_notEmpty() {
        val chars = preprocessor.getHandledCharacters()
        assertTrue("Should handle at least 30 characters", chars.size >= 30)
    }

    @Test
    fun getHandledCharacters_containsEmDash() {
        assertTrue(
            preprocessor.getHandledCharacters().contains('\u2014')
        )
    }

    @Test
    fun getHandledCharacters_containsSmartQuotes() {
        val chars = preprocessor.getHandledCharacters()
        assertTrue(chars.contains('\u2018'))
        assertTrue(chars.contains('\u2019'))
        assertTrue(chars.contains('\u201c'))
        assertTrue(chars.contains('\u201d'))
    }

    @Test
    fun getHandledCharacters_containsLigatures() {
        val chars = preprocessor.getHandledCharacters()
        assertTrue(chars.contains('\u00e6'))
        assertTrue(chars.contains('\u0153'))
        assertTrue(chars.contains('\u00df'))
    }

    // --- Integration: full pipeline ---

    @Test
    fun preprocess_fullPipeline_mixedSpecialChars() {
        // Simulates text from an EPUB with various special characters
        val input = "Mr. Smith\u2014a Dr. of philosophy\u2014said \u201cHello\u201d\u2026"
        val result = preprocessor.preprocess(input)
        // Mr. -> Mister
        assertTrue("Mr. should be expanded", result.contains("Mister"))
        // em-dash -> pause
        assertTrue("Em-dash should become pause", result.contains(", "))
        // Dr. -> Doctor
        assertTrue("Dr. should be expanded", result.contains("Doctor"))
        // smart quotes -> straight
        assertFalse("Smart quotes should be replaced", result.contains("\u201c"))
        assertFalse("Smart quotes should be replaced", result.contains("\u201d"))
        // ellipsis -> pause
        assertFalse("Ellipsis should be replaced", result.contains("\u2026"))
    }

    @Test
    fun preprocess_fullPipeline_ligaturesAndQuotes() {
        val input = "The \u0153uvre of Encyclop\u00e6dia\u2014a \u201cgreat\u201d work"
        val result = preprocessor.preprocess(input)
        assertTrue("oe ligature expanded", result.contains("oeuvre"))
        assertTrue("ae ligature expanded", result.contains("Encyclopaedia"))
        assertTrue("em-dash replaced", result.contains(", "))
        assertTrue("smart quotes replaced", result.contains("\""))
    }

    @Test
    fun preprocess_fullPipeline_abbreviationsAtBoundaries() {
        val input = "Prof. Dr. St. etc."
        val result = preprocessor.preprocess(input)
        assertTrue("Prof. expanded", result.contains("Professor"))
        assertTrue("Dr. expanded", result.contains("Doctor"))
        assertTrue("St. expanded", result.contains("Saint"))
        assertTrue("etc. expanded", result.contains("etcetera"))
    }

    @Test
    fun preprocess_whitespaceNormalization() {
        val input = "Hello    world   test"
        val result = preprocessor.preprocess(input)
        assertFalse("Multiple spaces should be collapsed", result.contains("    "))
    }

    @Test
    fun preprocess_preservesSentenceStructure() {
        val input = "First sentence. Second sentence! Third sentence?"
        val result = preprocessor.preprocess(input)
        assertTrue("Period preserved", result.contains("sentence."))
        assertTrue("Exclamation preserved", result.contains("sentence!"))
        assertTrue("Question mark preserved", result.contains("sentence?"))
    }
}
