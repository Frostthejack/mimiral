package com.mimiral.app.data.remote.opds

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for searching OPDS catalogs using OpenSearch 1.1.
 *
 * Handles search URL template expansion (OpenSearch 1.1 spec)
 * and delegates fetching/parsing to OpdsHttpClient.
 */
@Singleton
class OpdsSearchRepository @Inject constructor(
    private val httpClient: OpdsHttpClient
) {

    /**
     * Searches a catalog using OpenSearch 1.1.
     *
     * First fetches the catalog feed to discover the OpenSearch description,
     * then expands the search URL template with the query and fetches results.
     *
     * @param catalogUrl The catalog's root URL
     * @param query The search query
     * @param username Optional username for Basic Auth
     * @param password Optional password for Basic Auth
     * @return Result containing the search results feed
     */
    suspend fun searchCatalog(
        catalogUrl: String,
        query: String,
        username: String? = null,
        password: String? = null
    ): Result<OpdsFeed> {
        return try {
            // Step 1: Fetch the catalog feed to get the search URL
            val feedResult = httpClient.fetchFeed(catalogUrl, username, password)
            val feed = feedResult.getOrThrow()

            // Step 2: Fetch the OpenSearch description
            val searchUrl = feed.searchUrl
                ?: return Result.failure(
                    IllegalStateException("Catalog does not declare an OpenSearch URL")
                )

            val searchDesc = httpClient.fetchOpenSearchDescription(searchUrl, username, password)

            // Step 3: Get the search URL template
            val template = searchDesc.getSearchUrl("application/atom+xml")
                ?: searchDesc.getSearchUrl("application/xml")
                ?: searchDesc.getSearchUrl()
                ?: return Result.failure(
                    IllegalStateException("OpenSearch description has no search URL template")
                )

            // Step 4: Expand the template
            val searchQueryUrl = expandSearchTemplate(template, query, 1, 10)

            // Step 5: Execute the search
            httpClient.fetchFeed(searchQueryUrl, username, password)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Searches using a known OpenSearch URL template directly.
     * Skips the discovery step — useful when you already have the template.
     *
     * @param searchTemplate The OpenSearch URL template with {searchTerms} placeholders
     * @param query The search query
     * @param page The page number (1-based)
     * @param pageSize The number of results per page
     * @param username Optional username for Basic Auth
     * @param password Optional password for Basic Auth
     * @return Result containing the search results feed
     */
    suspend fun searchWithTemplate(
        searchTemplate: String,
        query: String,
        page: Int = 1,
        pageSize: Int = 10,
        username: String? = null,
        password: String? = null
    ): Result<OpdsFeed> {
        return try {
            val searchQueryUrl = expandSearchTemplate(searchTemplate, query, page, pageSize)
            httpClient.fetchFeed(searchQueryUrl, username, password)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Expands an OpenSearch 1.1 URL template.
     *
     * Replaces the following parameters:
     * - {searchTerms} -> URL-encoded query
     * - {startPage} -> page number
     * - {startIndex} -> startIndex
     * - {count} -> page size
     * - {language} -> wildcard
     * - {inputEncoding} -> UTF-8
     * - {outputEncoding} -> UTF-8
     */
    private fun expandSearchTemplate(
        template: String,
        query: String,
        page: Int,
        pageSize: Int
    ): String {
        val startIndex = (page - 1) * pageSize + 1
        return template
            .replace("{searchTerms}", java.net.URLEncoder.encode(query, "UTF-8"))
            .replace("{startPage}", page.toString())
            .replace("{startIndex}", startIndex.toString())
            .replace("{count}", pageSize.toString())
            .replace("{language}", "*")
            .replace("{inputEncoding}", "UTF-8")
            .replace("{outputEncoding}", "UTF-8")
    }
}
