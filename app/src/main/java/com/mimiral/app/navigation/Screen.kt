package com.mimiral.app.navigation

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Collections : Screen("collections")
    object ReadingLists : Screen("reading_lists")
    object Discover : Screen("discover")
    object AddBooks : Screen("add_books")
    object NowReading : Screen("now_reading")
    object Settings : Screen("settings")
    object KavitaSetup : Screen("kavita_setup")
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

    // Kavita series/volume browsing
    object KavitaSeries : Screen("kavita_series/{seriesId}") {
        fun createRoute(seriesId: Int): String = "kavita_series/$seriesId"
    }

    // Reading list detail
    object ReadingListDetail : Screen("reading_list_detail/{listId}") {
        fun createRoute(listId: Int): String = "reading_list_detail/$listId"
    }
}
