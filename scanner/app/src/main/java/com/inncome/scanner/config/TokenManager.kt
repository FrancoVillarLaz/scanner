package com.inncome.scanner.config

import android.content.Context
import android.content.SharedPreferences


class TokenManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveToken(accessToken: String, refreshToken: String, expiresInSeconds: Long) {
        val currentTime = System.currentTimeMillis()
        val expiresAt = currentTime + (expiresInSeconds * 1000)

        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putLong(KEY_EXPIRES_AT, expiresAt)
            apply()
        }
    }

    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getExpiresAt(): Long {
        return prefs.getLong(KEY_EXPIRES_AT, 0)
    }

    fun isTokenValid(): Boolean {
        val expiresAt = getExpiresAt()
        val currentTime = System.currentTimeMillis()
        return expiresAt > currentTime
    }

    fun clearTokens() {
        prefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_EXPIRES_AT)
            apply()
        }
    }

    fun isLoggedIn(): Boolean {
        return getAccessToken() != null && isTokenValid()
    }

    companion object {
        private const val PREFS_NAME = "inncome_scanner_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
}
