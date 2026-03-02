package com.example.droneservicesapp.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.droneservicesapp.Application
import com.example.droneservicesapp.core.util.LocaleUtils
import com.example.droneservicesapp.R
import com.jakewharton.processphoenix.ProcessPhoenix
import org.osmdroid.config.Configuration
import java.io.File
import java.text.DecimalFormat


class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var cacheSizePref: Preference? = null
    private var clearCachePref: Preference? = null


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        cacheSizePref = findPreference("offline_cache_size")
        clearCachePref = findPreference("offline_clear_cache")

        clearCachePref?.setOnPreferenceClickListener {
            val cacheDir = getOsmdroidTileCacheDir()
            if (cacheDir == null) {
                cacheSizePref?.summary = "Cache directory not found"
                return@setOnPreferenceClickListener true
            }

            val ok = deleteRecursively(cacheDir)
            if (ok) {
                cacheSizePref?.summary = "0 B"
                Toast.makeText(requireContext(), "Offline map cache cleared", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to clear cache (some files may be locked)", Toast.LENGTH_LONG).show()
                // Refresh to show what remains
                updateCacheSizeSummary()
            }
            true
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        onMavInterfaceSelection(
            preferenceManager.sharedPreferences?.getString("mavInterface", null)
        )

        val bottomNavigationView = activity?.findViewById<View>(R.id.bottom_nav_view)
        bottomNavigationView?.isVisible = false

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {

        if (!isAdded) return

        when (key) {
            "mavInterface" -> {
                onMavInterfaceSelection(sharedPreferences?.getString("mavInterface", null))
            }

            "language" -> {
                LocaleUtils.setSelectedLanguageId(
                    sharedPreferences?.getString("language", "default")
                )
                ProcessPhoenix.triggerRebirth(Application.getInstance().applicationContext)
            }
        }
    }


    private fun onMavInterfaceSelection(mavInterface: String?) {
        if (mavInterface == "Serial") {
            findPreference<Preference?>(getString(R.string.mavlink_lan_port_pref))?.isVisible =
                false
            findPreference<Preference?>("mavSerialBaud")?.isVisible = true
        } else if (mavInterface == "TCP" || mavInterface == "UDP") {
            findPreference<Preference?>(getString(R.string.mavlink_lan_port_pref))?.isVisible = true
            findPreference<Preference?>("mavSerialBaud")?.isVisible = false
        }
    }

    private fun updateCacheSizeSummary() {
        val cacheDir = getOsmdroidTileCacheDir()
        if (cacheDir == null || !cacheDir.exists()) {
            cacheSizePref?.summary = "0 B"
            return
        }
        val bytes = dirSizeBytes(cacheDir)
        cacheSizePref?.summary = formatBytes(bytes)
    }

    private fun getOsmdroidTileCacheDir(): File? {
        // Requires that osmdroid Configuration has been initialized in Application.onCreate()
        // (Configuration.getInstance().load(...))
        return try {
            Configuration.getInstance().osmdroidTileCache
        } catch (e: Exception) {
            null
        }
    }

    private fun dirSizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()

        var total = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            total += dirSizeBytes(f)
        }
        return total
    }

    private fun deleteRecursively(target: File): Boolean {
        if (!target.exists()) return true
        if (target.isDirectory) {
            val files = target.listFiles()
            if (files != null) {
                for (f in files) {
                    if (!deleteRecursively(f)) return false
                }
            }
        }
        return target.delete()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${DecimalFormat("#.##").format(kb)} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${DecimalFormat("#.##").format(mb)} MB"
        val gb = mb / 1024.0
        return "${DecimalFormat("#.##").format(gb)} GB"
    }



    override fun onStart() {
        super.onStart()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateCacheSizeSummary()
    }

}




















