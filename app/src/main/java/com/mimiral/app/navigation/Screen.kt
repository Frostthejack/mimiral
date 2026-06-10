package com.mimiral.app.navigation

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Collections : Screen("collections")
    object ReadingLists : Screen("reading_lists")
    object Discover : Screen("discover")
    object AddBooks : Screen("add_books")
    object NowReading : Screen("now_reading")
    object Stats : Screen("stats")
    object Settings : Screen("settings")
    object GestureSettings : Screen("gesture_settings")
    object Statistics : Screen("statistics")
    object KavitaStats : Screen("kavita_stats")
    object ReadingGoals : Screen("reading_goals")
    object KavitaSetup : Screen("kavita_setup")
    object KavitaScrobbling : Screen("kavita_scrobbling")
    object KavitaDeviceManagement : Screen("kavita_device_management")
    object ReadingPreferences : Screen("reading_preferences")
    object AccessibilitySettings : Screen("accessibility_settings")
    object LibraryPreferences : Screen("library_preferences")
    object EpubReader : Screen("epub_reader/{bookId}") {
        fun createRoute(bookId: Int): String = "epub_reader/$bookId"
    }
    object PdfReader : Screen("pdf_reader/{bookId}") {
        fun createRoute(bookId: Int): String = "pdf_reader/$bookId"
    }
    object DjvuReader : Screen("djvu_reader/{bookId}") {
        fun createRoute(bookId: Int): String = "djvu_reader/$bookId"
    }
    object TxtRtfReader : Screen("txt_rtf_reader/{bookId}") {
        fun createRoute(bookId: Int): String = "txt_rtf_reader/$bookId"
    }
    object MobiReader : Screen("mobi_reader/{bookId}") {
        fun createRoute(bookId: Int): String = "mobi_reader/$bookId"
    }
    object Fb2Reader : Screen("fb2_reader/{bookId}") {
        fun createRoute(bookId: Int): String = "fb2_reader/$bookId"
    }
    object ComicReader : Screen("comic_reader/{bookId}") {
        fun createRoute(bookId: Int): String = "comic_reader/$bookId"
    }
    object DocReader : Screen("doc_reader/{bookId}") {
        fun createRoute(bookId: Int): String = "doc_reader/$bookId"
    }
    object MarkdownReader : Screen("markdown_reader/{bookId}") {
        fun createRoute(bookId: Int): String = "markdown_reader/$bookId"
    }

    object ReadingMode : Screen("reading_mode/{bookId}") {
        fun createRoute(bookId: Int): String = "reading_mode/$bookId"
    }

    // TTS settings
    object TTSSettings : Screen("tts_settings")

    // Kavita series/volume browsing
    object KavitaSeries : Screen("kavita_series/{seriesId}") {
        fun createRoute(seriesId: Int): String = "kavita_series/$seriesId"
    }

    // Kavita collections browsing
    object KavitaCollections : Screen("kavita_collections")

    // Reading list detail
    object ReadingListDetail : Screen("reading_list_detail/{listId}") {
        fun createRoute(listId: Int): String = "reading_list_detail/$listId"
    }

    // Book metadata editing
    object EditBookMetadata : Screen("edit_book_metadata/{bookId}") {
        fun createRoute(bookId: Int): String = "edit_book_metadata/$bookId"
    }

    // Collection picker - accepts comma-separated book IDs
    object CollectionPicker : Screen("collection_picker/{bookIds}") {
        fun createRoute(bookIds: List<Int>): String =
            "collection_picker/${bookIds.joinToString(",")}"
    }

    // Kavita bookmark viewer
    object KavitaBookmarks : Screen("kavita_bookmarks")

    // Want To Read list (Kavita)
    object WantToRead : Screen("want_to_read")

    // Kavita OPDS feed categories (Collections, Reading Lists, WTR, On Deck, etc.)
    object KavitaOpdsFeeds : Screen("kavita_opds_feeds")

    // Kavita OPDS direct feed browsing (receives URL + title as args)
    object KavitaOpdsBrowse : Screen("kavita_opds_browse/{feedUrl}/{feedTitle}") {
        fun createRoute(feedUrl: String, feedTitle: String): String {
            val encodedUrl = java.net.URLEncoder.encode(feedUrl, "UTF-8")
            val encodedTitle = java.net.URLEncoder.encode(feedTitle, "UTF-8")
            return "kavita_opds_browse/$encodedUrl/$encodedTitle"
        }
    }

    // OPDS catalog management
    object OpdsCatalog : Screen("opds_catalog")

    // OPDS catalog browser (browse a specific catalog's entries)
    object OpdsCatalogBrowser : Screen("opds_catalog_browser")

    // Free sources browser
    object FreeSources : Screen("free_sources")

    // Kavita reading list detail
    object KavitaReadingList : Screen("kavita_reading_list")
}
