package com.mimiral.app.ui.reader

import androidx.compose.ui.graphics.Color

data class HighlightColor(
    val name: String,
    val color: Color,
    val hex: String
)

val highlightColors = listOf(
    HighlightColor("Yellow", Color(0xFFFFEB3B), "#FFFFEB3B"),
    HighlightColor("Green", Color(0xFF69F0AE), "#FF69F0AE"),
    HighlightColor("Blue", Color(0xFF448AFF), "#FF448AFF"),
    HighlightColor("Pink", Color(0xFFFF4081), "#FFFF4081"),
    HighlightColor("Orange", Color(0xFFFFAB40), "#FFFFAB40"),
    HighlightColor("Purple", Color(0xFFB388FF), "#FFB388FF")
)

fun parseHighlightColor(colorString: String): Color {
    return try {
        when {
            colorString.startsWith("#") -> Color(android.graphics.Color.parseColor(colorString))
            else -> highlightColors.firstOrNull {
                it.name.equals(colorString, ignoreCase = true)
            }?.color ?: highlightColors.first().color
        }
    } catch (_: Exception) {
        highlightColors.first().color
    }
}
