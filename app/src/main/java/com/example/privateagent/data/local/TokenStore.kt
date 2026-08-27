package com.example.privateagent.data.local

import android.annotation.SuppressLint
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context).
    setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context, FILE_NAME, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @SuppressLint("UseKtx")
    fun saveTokens(
        accessToken: String,
        refreshToken: String
    ): Boolean {
        return preferences.edit().
        putString(KEY_ACCESS_TOKEN, accessToken).
        putString(KEY_REFRESH_TOKEN, refreshToken).commit()
    }

    fun getAccessToken(): String? {
        return preferences.getString(KEY_ACCESS_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return preferences.getString(KEY_REFRESH_TOKEN, null)
    }

    @SuppressLint("UseKtx")
    fun clearTokens(): Boolean {
        return preferences.edit().
        remove(KEY_ACCESS_TOKEN).
        remove(KEY_REFRESH_TOKEN).commit()
    }

    companion object {
        private const val FILE_NAME = "auth_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}