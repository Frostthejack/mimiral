package com.mimiral.app.data.local.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStateTest {

    @Test
    fun `Idle - is ScanState instance`() {
        val state = ScanState.Idle
        assertTrue(state is ScanState)
    }

    @Test
    fun `Scanning - default values`() {
        val state = ScanState.Scanning()
        assertEquals(0, state.filesFound)
        assertEquals(0, state.filesProcessed)
        assertEquals(0, state.newFiles)
    }

    @Test
    fun `Scanning - custom values`() {
        val state = ScanState.Scanning(
            filesFound = 10,
            filesProcessed = 5,
            newFiles = 3
        )
        assertEquals(10, state.filesFound)
        assertEquals(5, state.filesProcessed)
        assertEquals(3, state.newFiles)
    }

    @Test
    fun `Completed - holds values`() {
        val state = ScanState.Completed(
            totalFound = 20,
            newFiles = 15,
            duplicatesSkipped = 5
        )
        assertEquals(20, state.totalFound)
        assertEquals(15, state.newFiles)
        assertEquals(5, state.duplicatesSkipped)
    }

    @Test
    fun `Error - holds message`() {
        val state = ScanState.Error("Something went wrong")
        assertEquals("Something went wrong", state.message)
        assertNull(state.cause)
    }

    @Test
    fun `Error - with cause`() {
        val cause = RuntimeException("Root cause")
        val state = ScanState.Error("Wrapper error", cause)
        assertEquals("Wrapper error", state.message)
        assertEquals(cause, state.cause)
    }

    @Test
    fun `ScanState - variants are distinct types`() {
        val idle = ScanState.Idle
        val scanning = ScanState.Scanning()
        val completed = ScanState.Completed(1, 1, 0)
        val error = ScanState.Error("err")

        assertTrue(idle is ScanState.Idle)
        assertTrue(scanning is ScanState.Scanning)
        assertTrue(completed is ScanState.Completed)
        assertTrue(error is ScanState.Error)
    }
}
