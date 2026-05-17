package com.mimiral.app.data.reader

/**
 * Represents margin crop values for PDF pages.
 * Each value is in pixels.
 */
data class MarginCrop(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    companion object {
        val NONE = MarginCrop(0, 0, 0, 0)

        fun uniform(percent: Int): MarginCrop {
            return MarginCrop(percent, percent, percent, percent)
        }
    }
}
