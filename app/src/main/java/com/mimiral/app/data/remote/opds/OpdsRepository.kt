package com.mimiral.app.data.remote.opds

import android.util.Log
import com.mimiral.app.data.local.dao.OpdsCatalogDao
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
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
 *
 * This is the single canonical OPDS repository. It uses OkHttp via OpdsClient
 * for all HTTP operations. The legacy data.repository.OpdsRepository (which used
 * raw HttpURLConnection) has been consolidated into this class.
 */
@Singleton
class OpdsRepository @Inject constructor(
    private val client: OpdsClient,
    private val parser: OpdsParser,
    private val catalogDao: OpdsCatalogDao
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
     * Get all active OPDS catalogs as a Flow.
     * Alias for getSavedCatalogs() — used by legacy callers.
     */
    fun getActiveCatalogs(): Flow<List<OpdsCatalogEntity>> =
        getSavedCatalogs()

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
            password = password,
            isActive = true
        )
        return catalogDao.insertCatalog(entity)
    }

    /**
     * Insert a catalog entity directly.
     * Legacy bridge — used by callers that construct the entity themselves.
     */
    suspend fun insertCatalog(catalog: OpdsCatalogEntity): Long =
        catalogDao.insertCatalog(catalog)

    /**
     * Remove an OPDS catalog.
     */
    suspend fun removeCatalog(catalog: OpdsCatalogEntity) {
        catalogDao.deleteCatalog(catalog)
    }

    /**
     * Delete a catalog entity directly.
     * Legacy bridge — delegates to removeCatalog().
     */
    suspend fun deleteCatalog(catalog: OpdsCatalogEntity) {
        removeCatalog(catalog)
    }

    /**
     * Fetch and parse an OPDS feed from a URL.
     * Legacy bridge returning Result<OpdsFeed> for backward compatibility.
     *
     * @param feedUrl The OPDS feed URL
     * @param username Optional username for HTTP Basic auth
     * @param password Optional password for HTTP Basic auth
     * @return Result containing the parsed OpdsFeed
     */
    suspend fun fetchFeed(
        feedUrl: String,
        username: String? = null,
        password: String? = null
    ): Result<OpdsFeed> {
        return when (val result = browseFeed(feedUrl, username, password)) {
            is OpdsResult.Success -> Result.success(result.data)
            is OpdsResult.Error -> Result.failure(
                Exception(result.message, result.cause)
            )
        }
    }

    /**
     * Download a book file from an OPDS acquisition link.
     * Legacy bridge returning Result<String> (destination path) for backward compatibility.
     *
     * @param downloadUrl The download URL
     * @param destinationPath The local file path to save to
     * @param username Optional username for HTTP Basic auth
     * @param password Optional password for HTTP Basic auth
     * @return Result containing the destination path on success
     */
    suspend fun downloadBook(
        downloadUrl: String,
        destinationPath: String,
        username: String? = null,
        password: String? = null
    ): Result<String> {
        return when (val result = client.downloadBook(downloadUrl, username, password)) {
            is OpdsResult.Success -> {
                try {
                    val destFile = java.io.File(destinationPath)
                    destFile.parentFile?.mkdirs()
                    destFile.writeBytes(result.data)
                    Result.success(destinationPath)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            is OpdsResult.Error -> Result.failure(
                Exception(result.message, result.cause)
            )
        }
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
            password = catalog.password,
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

        return browseFeed(searchUrl, catalog.username, catalog.password)
    }
}
