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
    object ReadingGoals : Screen("reading_goals")
    object KavitaSetup : Screen("kavita_setup")
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
}
