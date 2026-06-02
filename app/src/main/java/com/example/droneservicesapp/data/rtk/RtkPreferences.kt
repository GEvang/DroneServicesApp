package com.example.droneservicesapp.data.rtk

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class RtkPreferences(
    context: Context
) {
    private val appContext = context.applicationContext

    private val preferences: SharedPreferences by lazy {
        createEncryptedPreferencesWithRecovery().also { prefs ->
            migrateLegacyValuesIfNeeded(prefs)
        }
    }

    fun getConfig(): RtkConfig {
        return RtkConfig(
            ip = preferences.getString(KEY_IP, null)
                ?: preferences.getString(KEY_HOST_LEGACY, "").orEmpty(),
            port = preferences.getInt(KEY_PORT, DEFAULT_PORT),
            username = preferences.getString(KEY_USERNAME, "").orEmpty(),
            password = preferences.getString(KEY_PASSWORD, "").orEmpty(),
            mountpoint = preferences.getString(KEY_MOUNTPOINT, "").orEmpty(),
            mountpointLatitude = preferences.getFloat(KEY_MOUNTPOINT_LATITUDE, INVALID_COORDINATE_FLOAT)
                .takeIf { !it.isNaN() }
                ?.toDouble(),
            mountpointLongitude = preferences.getFloat(KEY_MOUNTPOINT_LONGITUDE, INVALID_COORDINATE_FLOAT)
                .takeIf { !it.isNaN() }
                ?.toDouble(),
            lastFetchSucceeded = preferences.getBoolean(KEY_LAST_FETCH_SUCCEEDED, false),
            lastStatusMessage = preferences.getString(KEY_LAST_STATUS_MESSAGE, "").orEmpty()
        )
    }

    fun saveIp(ip: String) {
        preferences.edit()
            .putString(KEY_IP, ip.trim())
            .remove(KEY_HOST_LEGACY)
            .apply()
    }

    fun savePort(port: Int) {
        preferences.edit().putInt(KEY_PORT, port).apply()
    }

    fun saveMountpoint(mountpoint: String) {
        preferences.edit()
            .putString(KEY_MOUNTPOINT, mountpoint.trim())
            .remove(KEY_MOUNTPOINT_LATITUDE)
            .remove(KEY_MOUNTPOINT_LONGITUDE)
            .apply()
    }

    fun saveMountpoint(mountpoint: RtkMountpoint) {
        preferences.edit()
            .putString(KEY_MOUNTPOINT, mountpoint.name.trim())
            .apply {
                if (mountpoint.hasCoordinates) {
                    putFloat(KEY_MOUNTPOINT_LATITUDE, mountpoint.latitude!!.toFloat())
                    putFloat(KEY_MOUNTPOINT_LONGITUDE, mountpoint.longitude!!.toFloat())
                } else {
                    remove(KEY_MOUNTPOINT_LATITUDE)
                    remove(KEY_MOUNTPOINT_LONGITUDE)
                }
            }
            .apply()
    }

    fun saveUsername(username: String) {
        preferences.edit().putString(KEY_USERNAME, username.trim()).apply()
    }

    fun savePassword(password: String) {
        preferences.edit().putString(KEY_PASSWORD, password).apply()
    }

    fun saveLastFetchSucceeded(succeeded: Boolean) {
        preferences.edit().putBoolean(KEY_LAST_FETCH_SUCCEEDED, succeeded).apply()
    }

    fun saveLastStatusMessage(message: String) {
        preferences.edit().putString(KEY_LAST_STATUS_MESSAGE, message).apply()
    }

    fun saveGpsStatus(fixType: Int, satellitesVisible: Int, hdop: Double?) {
        preferences.edit()
            .putInt(KEY_GPS_FIX_TYPE, fixType)
            .putInt(KEY_GPS_SATELLITES_VISIBLE, satellitesVisible)
            .putFloat(KEY_GPS_HDOP, hdop?.toFloat() ?: -1f)
            .apply()
    }

    fun getGpsStatus(): GpsStatus {
        val hdop = preferences.getFloat(KEY_GPS_HDOP, -1f).takeIf { it >= 0f }?.toDouble()
        return GpsStatus(
            fixType = preferences.getInt(KEY_GPS_FIX_TYPE, -1),
            satellitesVisible = preferences.getInt(KEY_GPS_SATELLITES_VISIBLE, 12),
            hdop = hdop
        )
    }

    private fun migrateLegacyValuesIfNeeded(prefs: SharedPreferences) {
        val editor = prefs.edit()

        if (!prefs.contains(KEY_IP) && prefs.contains(KEY_HOST_LEGACY)) {
            editor.putString(KEY_IP, prefs.getString(KEY_HOST_LEGACY, "").orEmpty())
        }
        editor
            .remove(KEY_HOST_LEGACY)
            .remove(KEY_ENABLED_LEGACY)
            .remove(KEY_TLS_LEGACY)
            .apply()
    }

    private fun createEncryptedPreferencesWithRecovery(): SharedPreferences {
        return try {
            createEncryptedPreferences()
        } catch (error: Exception) {
            Log.w(TAG, "RTK encrypted preferences could not be opened; resetting corrupted store", error)
            deleteEncryptedPreferencesStore()
            createEncryptedPreferences()
        }
    }

    private fun createEncryptedPreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun deleteEncryptedPreferencesStore() {
        appContext.deleteSharedPreferences(PREFS_FILE_NAME)
        appContext.deleteSharedPreferences(KEYSET_PREFS_FILE_NAME)
    }

    companion object {
        private const val TAG = "RtkPreferences"
        private const val PREFS_FILE_NAME = "rtk_secure_preferences"
        private const val KEYSET_PREFS_FILE_NAME = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        private const val DEFAULT_PORT = 2101
        private const val KEY_IP = "rtk_ip"
        private const val KEY_HOST_LEGACY = "rtk_host"
        private const val KEY_ENABLED_LEGACY = "rtk_enabled"
        private const val KEY_PORT = "rtk_port"
        private const val KEY_MOUNTPOINT = "rtk_mountpoint"
        private const val KEY_MOUNTPOINT_LATITUDE = "rtk_mountpoint_latitude"
        private const val KEY_MOUNTPOINT_LONGITUDE = "rtk_mountpoint_longitude"
        private const val KEY_USERNAME = "rtk_username"
        private const val KEY_PASSWORD = "rtk_password"
        private const val KEY_TLS_LEGACY = "rtk_tls"
        private const val KEY_LAST_FETCH_SUCCEEDED = "rtk_last_fetch_succeeded"
        private const val KEY_LAST_STATUS_MESSAGE = "rtk_last_status_message"
        private const val KEY_GPS_FIX_TYPE = "rtk_gps_fix_type"
        private const val KEY_GPS_SATELLITES_VISIBLE = "rtk_gps_satellites_visible"
        private const val KEY_GPS_HDOP = "rtk_gps_hdop"
        private const val INVALID_COORDINATE_FLOAT = Float.NaN
    }

    data class GpsStatus(
        val fixType: Int,
        val satellitesVisible: Int,
        val hdop: Double?
    )
}
