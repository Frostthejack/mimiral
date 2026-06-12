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
        private const val OPDS_PREFS_FILE = "opds_credentials"

        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"

        // OPDS-specific keys (stored in separate encrypted prefs file)
        private const val KEY_OPDS_PREFIX = "opds_catalog_"
    }

    private val masterKeyAlias by lazy {
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    }

    private val prefs by lazy {
        try {
            EncryptedSharedPreferences.create(
                PREFS_FILE,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences: ${e.message}", e)
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    /**
     * Separate encrypted prefs file for OPDS catalog credentials.
     * Each catalog's credentials are stored under a key prefixed with the catalog ID.
     */
    private val opdsPrefs by lazy {
        try {
            EncryptedSharedPreferences.create(
                OPDS_PREFS_FILE,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create OPDS EncryptedSharedPreferences: ${e.message}", e)
            context.getSharedPreferences(OPDS_PREFS_FILE, Context.MODE_PRIVATE)
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

    // ==================== OPDS Credentials ====================

    /**
     * Save OPDS catalog credentials to encrypted storage.
     *
     * @param catalogId The OPDS catalog ID
     * @param password Optional password for HTTP Basic auth
     * @param token Optional auth token
     */
    fun saveOpdsCredentials(catalogId: Int, password: String?, token: String?) {
        val keyPrefix = "$KEY_OPDS_PREFIX$catalogId"
        opdsPrefs.edit()
            .putString("${keyPrefix}_password", password)
            .putString("${keyPrefix}_token", token)
            .apply()
        Log.d(TAG, "OPDS credentials saved for catalog $catalogId")
    }

    /**
     * Get the stored password for an OPDS catalog.
     *
     * @param catalogId The OPDS catalog ID
     * @return The stored password, or null if not set
     */
    fun getOpdsPassword(catalogId: Int): String? {
        val key = "$KEY_OPDS_PREFIX${catalogId}_password"
        return opdsPrefs.getString(key, null)
    }

    /**
     * Get the stored token for an OPDS catalog.
     *
     * @param catalogId The OPDS catalog ID
     * @return The stored token, or null if not set
     */
    fun getOpdsToken(catalogId: Int): String? {
        val key = "$KEY_OPDS_PREFIX${catalogId}_token"
        return opdsPrefs.getString(key, null)
    }

    /**
     * Clear OPDS credentials for a specific catalog.
     *
     * @param catalogId The OPDS catalog ID
     */
    fun clearOpdsCredentials(catalogId: Int) {
        val keyPrefix = "$KEY_OPDS_PREFIX$catalogId"
        opdsPrefs.edit()
            .remove("${keyPrefix}_password")
            .remove("${keyPrefix}_token")
            .apply()
        Log.d(TAG, "OPDS credentials cleared for catalog $catalogId")
    }

    /**
     * Clear all OPDS credentials (all catalogs).
     */
    fun clearAllOpdsCredentials() {
        opdsPrefs.edit().clear().apply()
        Log.d(TAG, "All OPDS credentials cleared")
    }
}
