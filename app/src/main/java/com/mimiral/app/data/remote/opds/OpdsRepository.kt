package com.mimiral.app.data.remote.opds

import com.mimiral.app.data.local.dao.OpdsCatalogDao
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing OPDS catalogs and fetching/parsing OPDS feeds.
 *
 * Uses OkHttp (already in the dependency tree) for HTTP requests.
 */
@Singleton
class OpdsRepository @Inject constructor(
    private val opdsCatalogDao: OpdsCatalogDao,
    private val httpClient: OpdsHttpClient
) {

    /**
     * Returns all active OPDS catalogs from the local database.
     */
    fun getActiveCatalogs(): Flow<List<OpdsCatalogEntity>> =
        opdsCatalogDao.getActiveCatalogs()

    /**
     * Adds a new OPDS catalog.
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
        return opdsCatalogDao.insertCatalog(entity)
    }

    /**
     * Removes an OPDS catalog.
     */
    suspend fun removeCatalog(catalog: OpdsCatalogEntity) {
        opdsCatalogDao.deleteCatalog(catalog)
    }

    /**
     * Fetches and parses the main feed for a catalog.
     */
    suspend fun fetchCatalogFeed(catalog: OpdsCatalogEntity): Result<OpdsFeed> {
        return httpClient.fetchFeed(catalog.url, catalog.username, catalog.password)
    }

    /**
     * Fetches and parses an OPDS feed from a URL.
     */
    suspend fun fetchFeed(
        url: String,
        username: String? = null,
        password: String? = null
    ): Result<OpdsFeed> {
        return httpClient.fetchFeed(url, username, password)
    }

    /**
     * Fetches the OpenSearch description document from a catalog's search URL.
     */
    suspend fun fetchOpenSearchDescription(
        catalog: OpdsCatalogEntity
    ): Result<OpenSearchDescription> {
        val feedResult = fetchCatalogFeed(catalog)
        return feedResult.map { feed ->
            val searchUrl = feed.searchUrl
                ?: throw IllegalStateException("Catalog does not support OpenSearch")
            httpClient.fetchOpenSearchDescription(searchUrl, catalog.username, catalog.password)
        }
    }
}
