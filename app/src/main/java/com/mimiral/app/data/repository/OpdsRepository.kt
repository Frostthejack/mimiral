package com.mimiral.app.data.repository

import com.mimiral.app.data.local.dao.OpdsCatalogDao
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import com.mimiral.app.data.remote.opds.OpdsFeed
import com.mimiral.app.data.remote.opds.OpdsFeedParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import android.content.Context
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpdsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val opdsCatalogDao: OpdsCatalogDao
) {

    fun getActiveCatalogs(): Flow<List<OpdsCatalogEntity>> =
        opdsCatalogDao.getActiveCatalogs()

    suspend fun insertCatalog(catalog: OpdsCatalogEntity): Long =
        opdsCatalogDao.insertCatalog(catalog)

    suspend fun deleteCatalog(catalog: OpdsCatalogEntity) =
        opdsCatalogDao.deleteCatalog(catalog)

    /**
     * Fetch and parse an OPDS feed from a URL.
     * Supports HTTP Basic authentication via catalog credentials.
     */
    suspend fun fetchFeed(
        feedUrl: String,
        username: String? = null,
        password: String? = null
    ): Result<OpdsFeed> = withContext(Dispatchers.IO) {
        try {
            val url = URL(feedUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/atom+xml")

            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                val credentials = "$username:$password"
                val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
                connection.setRequestProperty("Authorization", "Basic $encoded")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(
                    Exception("HTTP $responseCode: ${connection.responseMessage}")
                )
            }

            val xml = BufferedInputStream(connection.inputStream).bufferedReader().use { it.readText() }
            connection.disconnect()

            val feed = OpdsFeedParser.parse(xml)
            Result.success(feed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download a book file from an OPDS acquisition link.
     * Returns the downloaded file path, or null on failure.
     */
    suspend fun downloadBook(
        downloadUrl: String,
        destinationPath: String,
        username: String? = null,
        password: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.requestMethod = "GET"

            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                val credentials = "$username:$password"
                val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
                connection.setRequestProperty("Authorization", "Basic $encoded")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(
                    Exception("HTTP $responseCode: ${connection.responseMessage}")
                )
            }

            val destFile = java.io.File(destinationPath)
            destFile.parentFile?.mkdirs()

            BufferedInputStream(connection.inputStream).use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()

            Result.success(destinationPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
