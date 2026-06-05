package com.mimiral.app.data.remote.kavita

import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for KavitaClient creation.
 *
 * Note: KavitaClient.create() builds a real Retrofit instance which requires
 * OkHttp runtime. These tests verify the factory method does not throw.
 * Full client behavior tests require integration tests with a mock server.
 */
class KavitaClientTest {

    @Test
    fun `create client with base url only`() {
        val client = KavitaClient.create("https://kavita.example.com/")
        assertNotNull(client)
    }

    @Test
    fun `create client with jwt token`() {
        val client = KavitaClient.create(
            baseUrl = "https://kavita.example.com/",
            token = "test-jwt-token"
        )
        assertNotNull(client)
    }

    @Test
    fun `create client with api key`() {
        val client = KavitaClient.create(
            baseUrl = "https://kavita.example.com/",
            apiKey = "test-api-key"
        )
        assertNotNull(client)
    }

    @Test
    fun `create client with both token and api key`() {
        val client = KavitaClient.create(
            baseUrl = "https://kavita.example.com/",
            token = "jwt-token",
            apiKey = "api-key"
        )
        assertNotNull(client)
    }
}
