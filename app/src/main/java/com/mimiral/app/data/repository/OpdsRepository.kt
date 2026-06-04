package com.mimiral.app.data.repository

import com.mimiral.app.data.local.dao.OpdsCatalogDao
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Result of validating an OPDS catalog URL.
 */
sealed class OpdsValidationResult {
    /** URL is a valid OPDS feed. [title] is the feed title if available. */
    data class Success(val title: String?) : OpdsValidationResult()

    /** URL could not be reached or returned an error. */
    data class Error(val message: String) : OpdsValidationResult()
}

/**
 * Repository for OPDS catalog management.
 * Handles CRUD operations and URL validation (fetch + parse OPDS feed).
 */
@Singleton
class OpdsRepository @Inject constructor(
    private val opdsCatalogDao: OpdsCatalogDao
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getAllCatalogs(): Flow<List<OpdsCatalogEntity>> =
        opdsCatalogDao.getAllCatalogs()

    fun getActiveCatalogs(): Flow<List<OpdsCatalogEntity>> =
        opdsCatalogDao.getActiveCatalogs()

    suspend fun getCatalogById(id: Int): OpdsCatalogEntity? =
        opdsCatalogDao.getCatalogById(id)

    suspend fun insertCatalog(catalog: OpdsCatalogEntity): Long =
        opdsCatalogDao.insertCatalog(catalog)

    suspend fun updateCatalog(catalog: OpdsCatalogEntity) =
        opdsCatalogDao.updateCatalog(catalog)

    suspend fun deleteCatalog(catalog: OpdsCatalogEntity) =
        opdsCatalogDao.deleteCatalog(catalog)

    /**
     * Validate an OPDS catalog URL by fetching it and checking for a valid
     * OPDS feed (Atom XML with OPDS namespace).
     *
     * Supports HTTP Basic Auth and URL token auth via the catalog entity.
     */
    suspend fun validateCatalogUrl(catalog: OpdsCatalogEntity): OpdsValidationResult {
        return try {
            val requestBuilder = Request.Builder()
                .url(buildUrlWithToken(catalog))
                .get()

            // Add HTTP Basic Auth header if credentials are present
            if (!catalog.username.isNullOrBlank() && !catalog.password.isNullOrBlank()) {
                val credentials = Credentials.basic(catalog.username, catalog.password)
                requestBuilder.header("Authorization", credentials)
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful) {
                return OpdsValidationResult.Error(
                    "Server returned HTTP ${response.code}"
                )
            }

            val body = response.body?.string()
                ?: return OpdsValidationResult.Error("Empty response body")

            // Parse the XML to verify it's a valid OPDS/Atom feed
            val title = parseOpdsFeedTitle(body)
            OpdsValidationResult.Success(title)
        } catch (e: java.net.UnknownHostException) {
            OpdsValidationResult.Error("Cannot resolve host: ${e.message}")
        } catch (e: java.net.SocketTimeoutException) {
            OpdsValidationResult.Error("Connection timed out")
        } catch (e: java.io.IOException) {
            OpdsValidationResult.Error("Network error: ${e.message}")
        } catch (e: Exception) {
            OpdsValidationResult.Error("Invalid OPDS feed: ${e.message}")
        }
    }

    /**
     * Build the URL with token appended if URL token auth is configured.
     */
    private fun buildUrlWithToken(catalog: OpdsCatalogEntity): String {
        if (catalog.authType == "TOKEN" && !catalog.token.isNullOrBlank()) {
            val separator = if (catalog.url.contains("?")) "&" else "?"
            return "${catalog.url}${separator}token=${catalog.token}"
        }
        return catalog.url
    }

    /**
     * Parse the OPDS/Atom feed XML and extract the feed title.
     * Returns null if the feed is valid but has no title.
     * Throws if the XML is not a valid Atom/OPDS feed.
     */
    private fun parseOpdsFeedTitle(xml: String): String? {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var title: String? = null
        var eventType = parser.eventType
        var foundFeedElement = false
        var foundEntryElement = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name
                    if (tagName == "feed") {
                        foundFeedElement = true
                    }
                    if (tagName == "entry") {
                        foundEntryElement = true
                    }
                    if (tagName == "title" && foundFeedElement && title == null) {
                        title = parser.nextText()
                    }
                }
            }
            eventType = parser.next()
        }

        // A valid OPDS feed must have a <feed> element (Atom)
        if (!foundFeedElement && !foundEntryElement) {
            throw IllegalArgumentException(
                "Not a valid OPDS/Atom feed (no <feed> or <entry> element found)"
            )
        }

        return title
    }
}
