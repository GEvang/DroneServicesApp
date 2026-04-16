package com.example.droneservicesapp.data.rtk

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class RtkPreferences(
    context: Context
) {
    private val appContext = context.applicationContext

    private val preferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getConfig(): RtkConfig {
        return RtkConfig(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            host = preferences.getString(KEY_HOST, "").orEmpty(),
            port = preferences.getInt(KEY_PORT, 2101),
            mountpoint = preferences.getString(KEY_MOUNTPOINT, "").orEmpty(),
            username = preferences.getString(KEY_USERNAME, "").orEmpty(),
            password = preferences.getString(KEY_PASSWORD, "").orEmpty(),
            useTls = preferences.getBoolean(KEY_TLS, false)
        )
    }

    fun saveEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun saveHost(host: String) {
        preferences.edit().putString(KEY_HOST, host.trim()).apply()
    }

    fun savePort(port: Int) {
        preferences.edit().putInt(KEY_PORT, port).apply()
    }

    fun saveMountpoint(mountpoint: String) {
        preferences.edit().putString(KEY_MOUNTPOINT, mountpoint.trim()).apply()
    }

    fun saveUsername(username: String) {
        preferences.edit().putString(KEY_USERNAME, username.trim()).apply()
    }

    fun savePassword(password: String) {
        preferences.edit().putString(KEY_PASSWORD, password).apply()
    }

    fun saveUseTls(useTls: Boolean) {
        preferences.edit().putBoolean(KEY_TLS, useTls).apply()
    }

    fun isConfigured(): Boolean {
        return RtkValidator.isValidConfig(getConfig())
    }

    companion object {
        private const val PREFS_FILE_NAME = "rtk_secure_preferences"
        private const val KEY_ENABLED = "rtk_enabled"
        private const val KEY_HOST = "rtk_host"
        private const val KEY_PORT = "rtk_port"
        private const val KEY_MOUNTPOINT = "rtk_mountpoint"
        private const val KEY_USERNAME = "rtk_username"
        private const val KEY_PASSWORD = "rtk_password"
        private const val KEY_TLS = "rtk_tls"
    }
}
