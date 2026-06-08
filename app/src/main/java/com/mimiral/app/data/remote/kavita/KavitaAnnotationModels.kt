package com.mimiral.app.data.remote.kavita

/**
 * Data models for the Kavita Annotation API.
 *
 * Annotations are user-created highlights with optional comments on text
 * within chapters. They support:
 * - Text selection highlighting (xPath-based for EPUB, page-based for PDF)
 * - Comments/notes on highlights
 * - Like/unlike on annotations (social feature with Kavita+)
 * - Spoiler tagging for sensitive content
 * - Export of annotations
 *
 * API endpoints:
 * - POST /api/Annotation/create — create annotation
 * - POST /api/Annotation/update — update annotation (comment, spoiler, etc.)
 * - POST /api/Annotation/delete — delete annotation
 * - GET  /api/Annotation/all?chapterId=X — all annotations for a chapter
 * - GET  /api/Annotation/all-for-series?seriesId=X — all annotations for a series
 * - POST /api/Annotation/like — like an annotation
 * - POST /api/Annotation/unlike — unlike an annotation
 * - GET  /api/Annotation/export?chapterId=X — export annotations
 */

// ==================== Request Models ====================

/**
 * Request body for creating a new annotation.
 * POST /api/Annotation/create
 *
 * @param xPath DOM-based XPath expression locating the highlighted text (EPUB).
 *              For non-EPUB formats, this can be empty/null.
 * @param selectedText The text content that was highlighted
 * @param pageNumber The page number where the annotation is located
 * @param chapterId The Kavita chapter ID
 * @param color Optional highlight color (hex string, e.g. "#FFFFEB3B")
 * @param note Optional comment/note text
 * @param isSpoiler Whether this annotation is marked as a spoiler
 */
data class KavitaAnnotationCreateRequest(
    val xPath: String? = null,
    val selectedText: String,
    val pageNumber: Int,
    val chapterId: Int,
    val color: String? = null,
    val note: String? = null,
    val isSpoiler: Boolean = false
)

/**
 * Request body for updating an existing annotation.
 * POST /api/Annotation/update
 *
 * @param id The annotation ID to update
 * @param note Updated comment/note text (null to clear)
 * @param isSpoiler Updated spoiler status
 * @param color Updated highlight color
 */
data class KavitaAnnotationUpdateRequest(
    val id: Int,
    val note: String? = null,
    val isSpoiler: Boolean? = null,
    val color: String? = null
)

/**
 * Request body for deleting an annotation.
 * POST /api/Annotation/delete
 *
 * @param id The annotation ID to delete
 */
data class KavitaAnnotationDeleteRequest(
    val id: Int
)

/**
 * Request body for liking/unliking an annotation.
 * POST /api/Annotation/like or /api/Annotation/unlike
 *
 * @param annotationId The annotation ID to like/unlike
 */
data class KavitaAnnotationLikeRequest(
    val annotationId: Int
)

// ==================== Response Models ====================

/**
 * A Kavita annotation (highlight + optional comment).
 * Returned by GET /api/Annotation/all and GET /api/Annotation/all-for-series.
 *
 * @param id Unique annotation ID
 * @param xPath DOM-based XPath locating the highlighted text
 * @param selectedText The highlighted text content
 * @param pageNumber Page number of the annotation
 * @param chapterId The Kavita chapter ID
 * @param seriesId The Kavita series ID
 * @param userId The user ID who created the annotation
 * @param username Display name of the annotation creator
 * @param note Comment/note text (optional)
 * @param isSpoiler Whether the annotation is marked as spoiler
 * @param color Highlight color (hex string)
 * @param likesCount Number of likes on this annotation
 * @param isLiked Whether the current user has liked this annotation
 * @param createdUtc Timestamp when the annotation was created (UTC)
 * @param lastModifiedUtc Timestamp of last modification (UTC)
 */
data class KavitaAnnotation(
    val id: Int,
    val xPath: String? = null,
    val selectedText: String = "",
    val pageNumber: Int = 0,
    val chapterId: Int = 0,
    val seriesId: Int = 0,
    val userId: Int = 0,
    val username: String = "",
    val note: String? = null,
    val isSpoiler: Boolean = false,
    val color: String? = null,
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val createdUtc: String? = null,
    val lastModifiedUtc: String? = null
)

/**
 * Wrapper for annotation export response.
 * GET /api/Annotation/export?chapterId=X
 *
 * Returns the exported annotations as a structured format
 * (typically JSON or text depending on export type).
 */
data class KavitaAnnotationExport(
    val annotations: List<KavitaAnnotation> = emptyList(),
    val chapterId: Int = 0,
    val seriesId: Int = 0,
    val exportedAt: String? = null
)
