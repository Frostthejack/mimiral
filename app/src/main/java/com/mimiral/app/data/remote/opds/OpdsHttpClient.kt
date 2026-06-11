package com.mimiral.app.data.remote.opds

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * HTTP client for fetching OPDS feeds and OpenSearch documents.
 *
 * Uses a shared OkHttpClient (provided by [OpdsModule]) with consistent
 * 30s timeouts for all OPDS operations.
 * Handles Basic Auth for catalogs that require authentication.
 */
@Singleton
class OpdsHttpClient @Inject constructor(
    private val client: OkHttpClient
) {

    /**
     * Fetches an OPDS feed (Atom XML) from the given URL.
     */
    suspend fun fetchFeed(
        url: String,
        username: String? = null,
        password: String? = null
    ): Result<OpdsFeed> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/atom+xml,application/xml,text/xml")

            if (username != null && password != null) {
                requestBuilder.header("Authorization", Credentials.basic(username, password))
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP ${response.code} for $url")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(
                    IOException("Empty response body for $url")
                )

            val feed = OpdsXmlParser.parseFeed(body)
            Result.success(feed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches an OpenSearch 1.1 description document.
     */
    suspend fun fetchOpenSearchDescription(
        url: String,
        username: String? = null,
        password: String? = null
    ): OpenSearchDescription = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/opensearchdescription+xml,application/xml,text/xml")

        if (username != null && password != null) {
            requestBuilder.header("Authorization", Credentials.basic(username, password))
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code} for $url")
        }

        val body = response.body?.string()
            ?: throw IOException("Empty response body for $url")

        OpdsXmlParser.parseOpenSearchDescription(body)
    }

    /**
     * Downloads a file from the given URL, writing to the output path.
     * Calls the progress callback with bytes downloaded so far.
     */
    suspend fun downloadFile(
        url: String,
        outputPath: String,
        username: String? = null,
        password: String? = null,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder().url(url)

            if (username != null && password != null) {
                requestBuilder.header("Authorization", Credentials.basic(username, password))
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP ${response.code} for $url")
                )
            }

            val body = response.body
                ?: return@withContext Result.failure(
                    IOException("Empty response body for $url")
                )

            val totalBytes = body.contentLength()
            val outputFile = java.io.File(outputPath)
            outputFile.parentFile?.mkdirs()

            var bytesDownloaded = 0L
            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        onProgress?.invoke(bytesDownloaded, totalBytes)
                    }
                }
            }

            Result.success(outputPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
