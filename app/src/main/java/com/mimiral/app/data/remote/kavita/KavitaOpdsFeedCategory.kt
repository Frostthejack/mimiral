package com.mimiral.app.data.remote.kavita

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents a Kavita OPDS feed category that can be browsed
 * via the OPDS protocol under /api/Opds/{apiKey}/.
 *
 * @param id Stable identifier used for navigation routes
 * @param label Display name in the UI
 * @param urlSuffix Path appended to the OPDS base URL (e.g. "Collections" → /api/Opds/{apiKey}/Collections)
 * @param icon Compose ImageVector for the drawer/nav item (not stored here — resolved in UI layer)
 */
enum class KavitaOpdsFeedCategory(
    val label: String,
    val urlSuffix: String
) {
    COLLECTIONS("Collections", "Collections"),
    READING_LISTS("Reading Lists", "ReadingLists"),
    WANT_TO_READ("Want To Read", "WantToRead"),
    ON_DECK("On Deck", "OnDeck"),
    RECENTLY_ADDED("Recently Added", "RecentlyAdded"),
    RECENTLY_UPDATED("Recently Updated", "RecentlyUpdated"),
    SMART_FILTERS("Smart Filters", "SmartFilters");

    /**
     * Build the full OPDS feed URL from the base URL.
     * Base URL format: https://kavita.example.com/api/opds/{apiKey}
     * Result format:   https://kavita.example.com/api/opds/{apiKey}/Collections
     */
    fun buildFeedUrl(opdsBaseUrl: String): String {
        return "${opdsBaseUrl.trimEnd('/')}/$urlSuffix"
    }
}
