package com.mimiral.app.data.remote.kavita

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for Kavita server credentials and tokens.
 *
 * Uses AndroidX Security library's [EncryptedSharedPreferences] to store
 * sensitive data (passwords, API keys, JWT tokens) in an encrypted format.
 * The encryption key is managed by the Android Keystore system.
 */
@Singleton
class KavitaCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "KavitaCredStore"
        private const val PREFS_FILE = "kavita_credentials"

        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }

    private val prefs by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.wtf(TAG, "SECURITY: Failed to create EncryptedSharedPreferences — " +
                "falling back to UNENCRYPTED SharedPreferences! " +
                "Passwords and API keys will be stored in plaintext. " +
                "Error: ${e.message}", e)
            // Fallback to regular SharedPreferences if encryption is not available
            // WARNING: This stores sensitive data (passwords, API keys, tokens)
            // in plaintext on device. Investigate and fix the root cause.
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    // ==================== Save Operations ====================

    /**
     * Save server URL.
     */
    fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
        Log.d(TAG, "Server URL saved")
    }

    /**
     * Save username/password credentials.
     */
    fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
        Log.d(TAG, "Credentials saved for user: $username")
    }

    /**
     * Save API key.
     */
    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
        Log.d(TAG, "API key saved")
    }

    /**
     * Save JWT token and optional refresh token.
     */
    fun saveTokens(token: String, refreshToken: String? = null) {
        prefs.edit()
            .putString(KEY_JWT_TOKEN, token)
            .apply()
        refreshToken?.let {
            prefs.edit().putString(KEY_REFRESH_TOKEN, it).apply()
        }
        Log.d(TAG, "JWT token saved")
    }

    /**
     * Save all connection info at once after successful login.
     */
    fun saveConnectionInfo(
        serverUrl: String,
        username: String,
        password: String,
        token: String,
        refreshToken: String? = null
    ) {
        prefs.edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_JWT_TOKEN, token)
            .apply()
        refreshToken?.let {
            prefs.edit().putString(KEY_REFRESH_TOKEN, it).apply()
        }
        Log.d(TAG, "Connection info saved for user: $username")
    }

    // ==================== Read Operations ====================

    fun getServerUrl(): String? = prefs.getString(KEY_SERVER_URL, null)

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun getJwtToken(): String? = prefs.getString(KEY_JWT_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    // ==================== Status Checks ====================

    /**
     * Check if stored credentials exist for username/password auth.
     */
    fun hasCredentials(): Boolean {
        return !getUsername().isNullOrBlank() && !getPassword().isNullOrBlank()
    }

    /**
     * Check if a stored API key exists.
     */
    fun hasApiKey(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    /**
     * Check if a stored JWT token exists.
     */
    fun hasJwtToken(): Boolean {
        return !getJwtToken().isNullOrBlank()
    }

    /**
     * Check if any form of authentication is available.
     */
    fun hasAnyAuth(): Boolean {
        return hasJwtToken() || hasApiKey() || hasCredentials()
    }

    // ==================== Clear Operations ====================

    /**
     * Clear all stored credentials and tokens.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All credentials cleared")
    }

    /**
     * Clear only the auth tokens (keep server URL and credentials).
     */
    fun clearTokens() {
        prefs.edit()
            .remove(KEY_JWT_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
        Log.d(TAG, "Auth tokens cleared")
    }
}
