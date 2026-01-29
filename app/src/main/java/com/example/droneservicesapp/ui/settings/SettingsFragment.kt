package com.example.droneservicesapp.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.droneservicesapp.Application
import com.example.droneservicesapp.LocaleUtils
import com.example.droneservicesapp.R
import com.jakewharton.processphoenix.ProcessPhoenix

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
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


    private fun onMavInterfaceSelection(mavInterface : String?)
    {
        if(mavInterface == "Serial") {
            findPreference<Preference?>( getString(R.string.mavlink_lan_port_pref) )?.isVisible = false
            findPreference<Preference?>("mavSerialBaud")?.isVisible = true
        }
        else if(mavInterface == "TCP" || mavInterface == "UDP" ){
            findPreference<Preference?>( getString(R.string.mavlink_lan_port_pref) )?.isVisible = true
            findPreference<Preference?>("mavSerialBaud")?.isVisible = false
        }
    }


    override fun onStart() {
        super.onStart()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

}




















