package com.example.droneservicesapp.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.example.droneservicesapp.Application
import com.example.droneservicesapp.core.util.LocaleUtils
import com.example.droneservicesapp.data.rtk.RtkConfig
import com.example.droneservicesapp.data.rtk.RtkPreferences
import com.example.droneservicesapp.data.rtk.RtkValidator
import com.example.droneservicesapp.R
import com.jakewharton.processphoenix.ProcessPhoenix
import org.osmdroid.config.Configuration
import java.io.File
import java.text.DecimalFormat


class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var cacheSizePref: Preference? = null
    private var clearCachePref: Preference? = null
    private lateinit var rtkPreferences: RtkPreferences
    private var rtkEnabledPref: SwitchPreferenceCompat? = null
    private var rtkHostPref: EditTextPreference? = null
    private var rtkPortPref: EditTextPreference? = null
    private var rtkMountpointPref: EditTextPreference? = null
    private var rtkUsernamePref: EditTextPreference? = null
    private var rtkPasswordPref: EditTextPreference? = null
    private var rtkTlsPref: SwitchPreferenceCompat? = null
    private var rtkTestConnectionPref: Preference? = null
    private var rtkStatusPref: Preference? = null
    private lateinit var rtkPreferenceDataStore: PreferenceDataStore


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        rtkPreferences = RtkPreferences(requireContext())
        rtkPreferenceDataStore = createRtkPreferenceDataStore()

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

        initRtkPreferences()
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

    private fun initRtkPreferences() {
        rtkEnabledPref = findPreference(getString(R.string.rtk_enabled_pref))
        rtkHostPref = findPreference(getString(R.string.rtk_host_pref))
        rtkPortPref = findPreference(getString(R.string.rtk_port_pref))
        rtkMountpointPref = findPreference(getString(R.string.rtk_mountpoint_pref))
        rtkUsernamePref = findPreference(getString(R.string.rtk_username_pref))
        rtkPasswordPref = findPreference(getString(R.string.rtk_password_pref))
        rtkTlsPref = findPreference(getString(R.string.rtk_tls_pref))
        rtkTestConnectionPref = findPreference(getString(R.string.rtk_test_connection_pref))
        rtkStatusPref = findPreference(getString(R.string.rtk_status_pref))

        clearLegacyRtkSharedPreferences()
        bindRtkPreferenceDataStore()

        val config = rtkPreferences.getConfig()
        rtkEnabledPref?.isChecked = config.enabled
        rtkHostPref?.text = config.host
        rtkPortPref?.text = config.port.toString()
        rtkMountpointPref?.text = config.mountpoint
        rtkUsernamePref?.text = config.username
        rtkPasswordPref?.text = config.password
        rtkTlsPref?.isChecked = config.useTls
        rtkStatusPref?.summary = getString(R.string.rtk_not_tested)

        rtkPortPref?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }

        rtkPasswordPref?.setOnBindEditTextListener { editText ->
            editText.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            editText.transformationMethod = PasswordTransformationMethod.getInstance()
        }

        rtkEnabledPref?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as? Boolean ?: false
            updateRtkPreferenceState(enabled)
            rtkStatusPref?.summary = getString(R.string.rtk_not_tested)
            true
        }

        rtkHostPref?.setOnPreferenceChangeListener { _, newValue ->
            val host = newValue?.toString().orEmpty().trim()
            updateRtkSummaries(host = host)
            rtkStatusPref?.summary = getString(R.string.rtk_not_tested)
            true
        }

        rtkPortPref?.setOnPreferenceChangeListener { _, newValue ->
            val portValue = newValue?.toString().orEmpty().trim()
            if (!validatePort(portValue)) {
                Toast.makeText(requireContext(), getString(R.string.rtk_port_invalid), Toast.LENGTH_SHORT).show()
                false
            } else {
                updateRtkSummaries(port = portValue)
                rtkStatusPref?.summary = getString(R.string.rtk_not_tested)
                true
            }
        }

        rtkMountpointPref?.setOnPreferenceChangeListener { _, newValue ->
            val mountpoint = newValue?.toString().orEmpty().trim()
            updateRtkSummaries(mountpoint = mountpoint)
            rtkStatusPref?.summary = getString(R.string.rtk_not_tested)
            true
        }

        rtkUsernamePref?.setOnPreferenceChangeListener { _, newValue ->
            val username = newValue?.toString().orEmpty().trim()
            updateRtkSummaries(username = username)
            rtkStatusPref?.summary = getString(R.string.rtk_not_tested)
            true
        }

        rtkPasswordPref?.setOnPreferenceChangeListener { _, newValue ->
            val password = newValue?.toString().orEmpty()
            updateRtkSummaries(password = password)
            rtkStatusPref?.summary = getString(R.string.rtk_not_tested)
            true
        }

        rtkTlsPref?.setOnPreferenceChangeListener { _, newValue ->
            rtkStatusPref?.summary = getString(R.string.rtk_not_tested)
            true
        }

        rtkTestConnectionPref?.setOnPreferenceClickListener {
            onTestRtkConnection()
            true
        }

        updateRtkPreferenceState(config.enabled)
        updateRtkSummaries(config)
    }

    private fun updateRtkPreferenceState(enabled: Boolean) {
        rtkHostPref?.isEnabled = enabled
        rtkPortPref?.isEnabled = enabled
        rtkMountpointPref?.isEnabled = enabled
        rtkUsernamePref?.isEnabled = enabled
        rtkPasswordPref?.isEnabled = enabled
        rtkTlsPref?.isEnabled = enabled
        rtkTestConnectionPref?.isEnabled = enabled
        rtkStatusPref?.isEnabled = enabled
    }

    private fun updateRtkSummaries(config: RtkConfig = rtkPreferences.getConfig()) {
        updateRtkSummaries(
            host = config.host,
            port = config.port.toString(),
            mountpoint = config.mountpoint,
            username = config.username,
            password = config.password
        )
    }

    private fun updateRtkSummaries(
        host: String? = rtkHostPref?.text,
        port: String? = rtkPortPref?.text,
        mountpoint: String? = rtkMountpointPref?.text,
        username: String? = rtkUsernamePref?.text,
        password: String? = rtkPasswordPref?.text
    ) {
        rtkHostPref?.summary = host.orEmpty()
        rtkPortPref?.summary = port.orEmpty()
        rtkMountpointPref?.summary = mountpoint.orEmpty()
        rtkUsernamePref?.summary = username.orEmpty()
        rtkPasswordPref?.summary = if (password.isNullOrEmpty()) "" else "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"
    }

    private fun validatePort(value: String): Boolean {
        val port = value.toIntOrNull() ?: return false
        return RtkValidator.isValidPort(port)
    }

    private fun bindRtkPreferenceDataStore() {
        rtkEnabledPref?.preferenceDataStore = rtkPreferenceDataStore
        rtkHostPref?.preferenceDataStore = rtkPreferenceDataStore
        rtkPortPref?.preferenceDataStore = rtkPreferenceDataStore
        rtkMountpointPref?.preferenceDataStore = rtkPreferenceDataStore
        rtkUsernamePref?.preferenceDataStore = rtkPreferenceDataStore
        rtkPasswordPref?.preferenceDataStore = rtkPreferenceDataStore
        rtkTlsPref?.preferenceDataStore = rtkPreferenceDataStore
    }

    private fun clearLegacyRtkSharedPreferences() {
        preferenceManager.sharedPreferences?.edit()
            ?.remove(getString(R.string.rtk_enabled_pref))
            ?.remove(getString(R.string.rtk_host_pref))
            ?.remove(getString(R.string.rtk_port_pref))
            ?.remove(getString(R.string.rtk_mountpoint_pref))
            ?.remove(getString(R.string.rtk_username_pref))
            ?.remove(getString(R.string.rtk_password_pref))
            ?.remove(getString(R.string.rtk_tls_pref))
            ?.apply()
    }

    private fun createRtkPreferenceDataStore(): PreferenceDataStore {
        return object : PreferenceDataStore() {
            override fun putString(key: String?, value: String?) {
                when (key) {
                    getString(R.string.rtk_host_pref) -> rtkPreferences.saveHost(value.orEmpty())
                    getString(R.string.rtk_port_pref) -> {
                        val port = value?.toIntOrNull() ?: return
                        rtkPreferences.savePort(port)
                    }

                    getString(R.string.rtk_mountpoint_pref) -> rtkPreferences.saveMountpoint(value.orEmpty())
                    getString(R.string.rtk_username_pref) -> rtkPreferences.saveUsername(value.orEmpty())
                    getString(R.string.rtk_password_pref) -> rtkPreferences.savePassword(value.orEmpty())
                }
            }

            override fun putBoolean(key: String?, value: Boolean) {
                when (key) {
                    getString(R.string.rtk_enabled_pref) -> rtkPreferences.saveEnabled(value)
                    getString(R.string.rtk_tls_pref) -> rtkPreferences.saveUseTls(value)
                }
            }

            override fun getString(key: String?, defValue: String?): String {
                val config = rtkPreferences.getConfig()
                return when (key) {
                    getString(R.string.rtk_host_pref) -> config.host
                    getString(R.string.rtk_port_pref) -> config.port.toString()
                    getString(R.string.rtk_mountpoint_pref) -> config.mountpoint
                    getString(R.string.rtk_username_pref) -> config.username
                    getString(R.string.rtk_password_pref) -> config.password
                    else -> defValue.orEmpty()
                }
            }

            override fun getBoolean(key: String?, defValue: Boolean): Boolean {
                val config = rtkPreferences.getConfig()
                return when (key) {
                    getString(R.string.rtk_enabled_pref) -> config.enabled
                    getString(R.string.rtk_tls_pref) -> config.useTls
                    else -> defValue
                }
            }
        }
    }

    private fun onTestRtkConnection() {
        val config = rtkPreferences.getConfig()
        if (!RtkValidator.isValidConfig(config)) {
            rtkStatusPref?.summary = getString(R.string.rtk_failed)
            Toast.makeText(requireContext(), getString(R.string.invalid_rtk_settings), Toast.LENGTH_SHORT).show()
            return
        }

        rtkStatusPref?.summary = getString(R.string.rtk_not_tested)
        Toast.makeText(
            requireContext(),
            getString(R.string.rtk_connection_test_not_implemented),
            Toast.LENGTH_SHORT
        ).show()
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
        updateRtkSummaries()
        updateRtkPreferenceState(rtkEnabledPref?.isChecked == true)
    }

}




















