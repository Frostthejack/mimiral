package com.mimiral.app.data.remote.opds

import android.util.Log
import com.mimiral.app.data.local.dao.OpdsCatalogDao
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import com.mimiral.app.data.remote.kavita.KavitaCredentialStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Repository for OPDS catalog operations.
 *
 * Combines the OPDS HTTP client and parser to provide:
 * - Fetching and parsing OPDS feeds from remote catalogs
 * - Managing saved OPDS catalogs (CRUD)
 * - Browsing catalog entries and downloading books
 */
@Singleton
class OpdsRepository @Inject constructor(
    private val client: OpdsClient,
    private val parser: OpdsParser,
    private val catalogDao: OpdsCatalogDao,
    private val credentialStore: KavitaCredentialStore
) {
    companion object {
        private const val TAG = "OpdsRepository"
    }

    /**
     * Get all saved OPDS catalogs as a Flow.
     */
    fun getSavedCatalogs(): Flow<List<OpdsCatalogEntity>> =
        catalogDao.getActiveCatalogs()

    /**
     * Add a new OPDS catalog.
     *
     * @param name Display name for the catalog
     * @param url The OPDS feed URL
     * @param username Optional username for HTTP Basic auth
     * @param password Optional password for HTTP Basic auth
     * @return The ID of the newly inserted catalog, or -1 on failure
     */
    suspend fun addCatalog(
        name: String,
        url: String,
        username: String? = null,
        password: String? = null
    ): Long {
        val entity = OpdsCatalogEntity(
            name = name,
            url = url,
            username = username,
            isActive = true
        )
        val id = catalogDao.insertCatalog(entity)
        // Save sensitive credentials to encrypted storage
        if (id > 0) {
            credentialStore.saveOpdsCredentials(id.toInt(), password, null)
        }
        return id
    }

    /**
     * Remove an OPDS catalog.
     */
    suspend fun removeCatalog(catalog: OpdsCatalogEntity) {
        catalogDao.deleteCatalog(catalog)
    }

    /**
     * Fetch and parse an OPDS feed from a saved catalog.
     *
     * @param catalog The saved catalog entity
     * @return [OpdsResult] containing the parsed [OpdsFeed]
     */
    suspend fun browseCatalog(
        catalog: OpdsCatalogEntity
    ): OpdsResult<OpdsFeed> {
        Log.d(TAG, "Browsing catalog: ${catalog.name} at ${catalog.url}")
        return client.fetchAndParseFeed(
            url = catalog.url,
            username = catalog.username,
            password = credentialStore.getOpdsPassword(catalog.id),
            parser = parser
        )
    }

    /**
     * Fetch and parse an OPDS feed from a URL.
     *
     * @param url The OPDS feed URL
     * @param username Optional username for HTTP Basic auth
     * @param password Optional password for HTTP Basic auth
     * @return [OpdsResult] containing the parsed [OpdsFeed]
     */
    suspend fun browseFeed(
        url: String,
        username: String? = null,
        password: String? = null
    ): OpdsResult<OpdsFeed> {
        Log.d(TAG, "Browsing OPDS feed: $url")
        return client.fetchAndParseFeed(
            url = url,
            username = username,
            password = password,
            parser = parser
        )
    }

    /**
     * Navigate to a sub-catalog or next page via a navigation link.
     *
     * @param link The navigation link from a feed or entry
     * @param username Optional username for HTTP Basic auth
     * @param password Optional password for HTTP Basic auth
     * @return [OpdsResult] containing the parsed [OpdsFeed]
     */
    suspend fun navigateToLink(
        link: OpdsLink,
        username: String? = null,
        password: String? = null
    ): OpdsResult<OpdsFeed> {
        Log.d(TAG, "Navigating to: ${link.href}")
        return browseFeed(link.href, username, password)
    }

    /**
     * Download a book from an OPDS acquisition link.
     *
     * @param link The acquisition link from an entry
     * @param username Optional username for HTTP Basic auth
     * @param password Optional password for HTTP Basic auth
     * @return [OpdsResult] containing the raw book bytes
     */
    suspend fun downloadBook(
        link: OpdsLink,
        username: String? = null,
        password: String? = null
    ): OpdsResult<ByteArray> {
        Log.d(TAG, "Downloading book from: ${link.href}")
        return client.downloadBook(link.href, username, password)
    }

    /**
     * Search within an OPDS catalog using the OpenSearch URL template.
     *
     * @param catalog The OPDS catalog
     * @param query The search query
     * @return [OpdsResult] containing the parsed search results [OpdsFeed]
     */
    suspend fun searchCatalog(
        catalog: OpdsCatalogEntity,
        query: String
    ): OpdsResult<OpdsFeed> {
        // First browse the catalog to find the OpenSearch URL template
        val feedResult = browseCatalog(catalog)
        if (feedResult is OpdsResult.Error) return feedResult

        val feed = (feedResult as OpdsResult.Success).data
        val searchLink = feed.links.firstOrNull {
            it.rel == "search" && it.type?.contains("opensearchdescription") == true ||
                it.rel == "search"
        }

        if (searchLink == null) {
            return OpdsResult.Error("No search endpoint found in catalog")
        }

        // Extract the actual search URL from the link
        // OPDS search links may use OpenSearch URL templates with {searchTerms}
        val searchUrl = searchLink.href.replace("{searchTerms}", query)
            .replace("{count}", "20")
            .replace("{startIndex}", "0")
            .replace("{startPage}", "0")
            .replace("{language}", "*")
            .replace("{inputEncoding}", "UTF-8")
            .replace("{outputEncoding}", "UTF-8")

        return browseFeed(searchUrl, catalog.username, credentialStore.getOpdsPassword(catalog.id))
    }
}
