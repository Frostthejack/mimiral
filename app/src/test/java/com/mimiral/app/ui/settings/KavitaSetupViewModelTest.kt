package com.mimiral.app.ui.settings

import com.mimiral.app.data.remote.ConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KavitaSetupViewModelTest {

    @Test
    fun `KavitaSetupUiState - default values`() {
        val state = KavitaSetupUiState()

        assertEquals("", state.serverUrl)
        assertEquals(AuthMethod.USERNAME_PASSWORD, state.authMethod)
        assertEquals("", state.username)
        assertEquals("", state.password)
        assertEquals("", state.apiKey)
        assertEquals(ConnectionStatus.DISCONNECTED, state.connectionStatus)
        assertNull(state.serverInfo)
        assertNull(state.errorMessage)
        assertFalse(state.isTestingConnection)
        assertFalse(state.isSaving)
        assertFalse(state.isSaved)
        assertFalse(state.hasExistingConfig)
    }

    @Test
    fun `KavitaSetupUiState - copy with values`() {
        val state = KavitaSetupUiState(
            serverUrl = "https://kavita.example.com",
            authMethod = AuthMethod.API_KEY,
            apiKey = "my-key",
            isTestingConnection = true
        )

        assertEquals("https://kavita.example.com", state.serverUrl)
        assertEquals(AuthMethod.API_KEY, state.authMethod)
        assertEquals("my-key", state.apiKey)
        assertTrue(state.isTestingConnection)
    }

    @Test
    fun `AuthMethod - has two values`() {
        val values = AuthMethod.values()
        assertEquals(2, values.size)
        assertNotNull(values.find { it.name == "USERNAME_PASSWORD" })
        assertNotNull(values.find { it.name == "API_KEY" })
    }
}
