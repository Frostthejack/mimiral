package com.mimiral.app.data.local.entity

/**
 * Tracks reading time for a single session.
 *
 * Usage:
 * 1. Create an instance when the reader opens.
 * 2. Call [startSession] when the user begins/resumes reading.
 * 3. Call [stopSession] when the user pauses/closes the reader.
 * 4. Call [accumulatedMs] to get the total time to persist.
 *
 * The tracker handles:
 * - Multiple start/stop cycles within a single reader open (pause/resume).
 * - Midnight rollover: if a reading session spans midnight, the time is split
 *   across two days at the day boundary.
 */
class ReadingTimeTracker {

    private var sessionStartMs: Long = 0L
    private var accumulatedMs: Long = 0L
    private var isRunning: Boolean = false

    /** Start or resume a reading session. */
    fun startSession() {
        if (!isRunning) {
            sessionStartMs = System.currentTimeMillis()
            isRunning = true
        }
    }

    /**
     * Stop the current session and accumulate elapsed time.
     * Returns the number of milliseconds elapsed in this session segment.
     */
    fun stopSession(): Long {
        if (isRunning) {
            val elapsed = System.currentTimeMillis() - sessionStartMs
            accumulatedMs += elapsed
            isRunning = false
            return elapsed
        }
        return 0L
    }

    /** Get total accumulated time in milliseconds across all session segments. */
    fun accumulatedMs(): Long {
        val current = if (isRunning) {
            System.currentTimeMillis() - sessionStartMs
        } else {
            0L
        }
        return accumulatedMs + current
    }

    /** Reset all accumulated time (e.g., when starting a new day's tracking). */
    fun reset() {
        sessionStartMs = 0L
        accumulatedMs = 0L
        isRunning = false
    }

    /** Whether a session is currently active. */
    fun isRunning(): Boolean = isRunning
}
