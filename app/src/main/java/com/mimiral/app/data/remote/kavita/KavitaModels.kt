package com.mimiral.app.data.remote.kavita

import com.google.gson.annotations.SerializedName

/**
 * Kavita API response for GET /api/Library.
 * Represents a Kavita library (can be books, comics, etc.)
 */
data class KavitaLibrary(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: Int,
    @SerializedName("lastScanned") val lastScanned: String? = null
) {
    companion object {
        const val TYPE_BOOK = 0
        const val TYPE_COMIC = 1
        const val TYPE_MANGA = 2
    }

    val isBookLibrary: Boolean
        get() = type == TYPE_BOOK

    val isComicLibrary: Boolean
        get() = type == TYPE_COMIC || type == TYPE_MANGA

    val typeLabel: String
        get() = when (type) {
            TYPE_BOOK -> "Books"
            TYPE_COMIC -> "Comics"
            TYPE_MANGA -> "Manga"
            else -> "Unknown"
        }
}

/**
 * Kavita API response for GET /api/Series/metadata.
 * Represents a series (collection of volumes/issues).
 */
data class KavitaSeries(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("originalName") val originalName: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("pages") val pages: Int = 0,
    @SerializedName("format") val format: Int = 0,
    @SerializedName("created") val created: String? = null,
    @SerializedName("lastModified") val lastModified: String? = null,
    @SerializedName("coverImage") val coverImage: String? = null,
    @SerializedName("primaryColor") val primaryColor: String? = null,
    @SerializedName("secondaryColor") val secondaryColor: String? = null,
    @SerializedName("volumes") val volumes: List<KavitaVolume> = emptyList()
) {
    companion object {
        const val FORMAT_BOOK = 0
        const val FORMAT_COMIC = 1
        const val FORMAT_MANGA = 2
    }

    val formatLabel: String
        get() = when (format) {
            FORMAT_BOOK -> "Book"
            FORMAT_COMIC -> "Comic"
            FORMAT_MANGA -> "Manga"
            else -> "Unknown"
        }
}

/**
 * Kavita API response for a volume within a series.
 * Represents a single book/issue/volume.
 */
data class KavitaVolume(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("number") val number: Int = 0,
    @SerializedName("pages") val pages: Int = 0,
    @SerializedName("created") val created: String? = null,
    @SerializedName("lastModified") val lastModified: String? = null,
    @SerializedName("coverImage") val coverImage: String? = null,
    @SerializedName("primaryColor") val primaryColor: String? = null,
    @SerializedName("secondaryColor") val secondaryColor: String? = null,
    @SerializedName("chapters") val chapters: List<KavitaChapter> = emptyList()
)

/**
 * Kavita chapter within a volume.
 */
data class KavitaChapter(
    @SerializedName("id") val id: Int,
    @SerializedName("number") val number: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("pages") val pages: Int = 0,
    @SerializedName("coverImage") val coverImage: String? = null,
    @SerializedName("created") val created: String? = null
)

/**
 * Simplified book entry for browsing display.
 * Constructed from KavitaSeries/Volume data.
 */
data class KavitaBook(
    val id: Int,
    val seriesId: Int,
    val seriesName: String,
    val title: String,
    val volumeNumber: Int,
    val author: String? = null,
    val description: String? = null,
    val pageCount: Int,
    val format: String,
    val coverImage: String? = null,
    val libraryId: Int,
    val libraryName: String,
    val libraryType: String
)

/**
 * Result wrapper for Kavita API operations.
 */
sealed class KavitaResult<out T> {
    data class Success<T>(val data: T) : KavitaResult<T>()
    data class Error(
        val message: String,
        val code: Int? = null,
        val cause: Throwable? = null
    ) : KavitaResult<Nothing>()
}
