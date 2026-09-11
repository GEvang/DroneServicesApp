package com.example.droneservicesapp.ui.mavlink

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R

class MavlinkNetworkFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var preferences: SharedPreferences
    private lateinit var interfaceSummary: TextView
    private lateinit var localPortSummary: TextView
    private lateinit var targetHostSummary: TextView
    private lateinit var targetPortSummary: TextView
    private lateinit var gcsSystemIdSummary: TextView
    private lateinit var bridgeSwitch: SwitchCompat
    private lateinit var qgcHostSummary: TextView
    private lateinit var qgcPortSummary: TextView
    private lateinit var bridgeStatus: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        requireActivity().findViewById<View>(R.id.bottom_nav_view)?.isVisible = false

        val padding = resources.getDimensionPixelSize(R.dimen.ds_space_lg)
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, resources.getDimensionPixelSize(R.dimen.ds_space_xl))
        }
        content.addView(createConnectionPanel())
        content.addView(createBridgePanel())

        refreshSummaries()
        return ScrollView(requireContext()).apply {
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ds_color_background))
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun createConnectionPanel(): View = createPanel(getString(R.string.drone_con_props_title_pref)).apply {
        addView(createSettingRow(getString(R.string.drone_conn_interface_pref)) {
            showInterfaceDialog()
        }.also { interfaceSummary = it.findViewWithTag(SUMMARY_TAG) })
        addView(createSettingRow(getString(R.string.mavlink_local_port_title)) {
            showTextDialog(R.string.mavlink_local_port_title, R.string.mavlink_lan_port_pref, "14550", true)
        }.also { localPortSummary = it.findViewWithTag(SUMMARY_TAG) })
        addView(createSettingRow(getString(R.string.mavlink_aircraft_host_title)) {
            showTextDialog(R.string.mavlink_aircraft_host_title, R.string.mavlink_target_host_pref, "", false)
        }.also { targetHostSummary = it.findViewWithTag(SUMMARY_TAG) })
        addView(createSettingRow(getString(R.string.mavlink_aircraft_port_title)) {
            showTextDialog(R.string.mavlink_aircraft_port_title, R.string.mavlink_target_port_pref, "14550", true)
        }.also { targetPortSummary = it.findViewWithTag(SUMMARY_TAG) })
        addView(createSettingRow(getString(R.string.mavlink_gcs_system_id_title)) {
            showTextDialog(
                R.string.mavlink_gcs_system_id_title,
                R.string.mavlink_gcs_system_id_pref,
                "254",
                numeric = true,
                numericRange = 1..255,
                invalidNumericMessage = R.string.mavlink_invalid_system_id,
            )
        }.also { gcsSystemIdSummary = it.findViewWithTag(SUMMARY_TAG) })
    }

    private fun createBridgePanel(): View = createPanel(getString(R.string.mavlink_bridge_section_title)).apply {
        addView(TextView(requireContext()).apply {
            text = getString(R.string.mavlink_bridge_description)
            setTextAppearance(R.style.TextAppearance_DroneServices_StatusValue)
            setPadding(0, resources.getDimensionPixelSize(R.dimen.ds_space_sm), 0, resources.getDimensionPixelSize(R.dimen.ds_space_sm))
        })
        addView(SwitchCompat(requireContext()).apply {
            bridgeSwitch = this
            text = getString(R.string.mavlink_bridge_enabled_title)
            setTextAppearance(R.style.TextAppearance_DroneServices_StatusLabel)
            setPadding(0, resources.getDimensionPixelSize(R.dimen.ds_space_md), 0, resources.getDimensionPixelSize(R.dimen.ds_space_md))
            setOnCheckedChangeListener { _, enabled ->
                preferences.edit { putBoolean(getString(R.string.mavlink_bridge_enabled_pref), enabled) }
                refreshSummaries()
            }
        })
        addView(createSettingRow(getString(R.string.mavlink_qgc_host_title)) {
            showTextDialog(R.string.mavlink_qgc_host_title, R.string.mavlink_bridge_host_pref, "", false)
        }.also { qgcHostSummary = it.findViewWithTag(SUMMARY_TAG) })
        addView(createSettingRow(getString(R.string.mavlink_qgc_port_title)) {
            showTextDialog(R.string.mavlink_qgc_port_title, R.string.mavlink_bridge_port_pref, "14550", true)
        }.also { qgcPortSummary = it.findViewWithTag(SUMMARY_TAG) })
        addView(TextView(requireContext()).apply {
            bridgeStatus = this
            setTextAppearance(R.style.TextAppearance_DroneServices_StatusValue)
            setPadding(0, resources.getDimensionPixelSize(R.dimen.ds_space_md), 0, 0)
        })
    }

    private fun createPanel(title: String): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_ds_overlay_card)
        val padding = resources.getDimensionPixelSize(R.dimen.ds_space_lg)
        setPadding(padding, padding, padding, padding)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = resources.getDimensionPixelSize(R.dimen.ds_space_lg)
        }
        addView(TextView(requireContext()).apply {
            text = title
            setTextAppearance(R.style.TextAppearance_DroneServices_MapPanelTitle)
        })
    }

    private fun createSettingRow(title: String, onClick: () -> Unit): LinearLayout {
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.ds_space_md)
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(0, verticalPadding, 0, verticalPadding)
            setOnClickListener { onClick() }
            addView(TextView(requireContext()).apply {
                text = title
                setTextAppearance(R.style.TextAppearance_DroneServices_StatusLabel)
            })
            addView(TextView(requireContext()).apply {
                tag = SUMMARY_TAG
                setTextAppearance(R.style.TextAppearance_DroneServices_StatusValue)
                setPadding(0, resources.getDimensionPixelSize(R.dimen.ds_space_xs), 0, 0)
            })
        }
    }

    private fun showInterfaceDialog() {
        val key = getString(R.string.mavlink_interface_pref)
        val values = resources.getStringArray(R.array.drone_connection_interfaces)
        val current = preferences.getString(key, "UDP") ?: "UDP"
        AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(R.string.drone_conn_interface_pref)
            .setSingleChoiceItems(values, values.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                preferences.edit { putString(key, values[which]) }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTextDialog(
        titleRes: Int,
        keyRes: Int,
        defaultValue: String,
        numeric: Boolean,
        numericRange: IntRange = 1..65535,
        invalidNumericMessage: Int = R.string.mavlink_invalid_port,
    ) {
        val key = getString(keyRes)
        val current = preferences.getString(key, defaultValue) ?: defaultValue
        val input = EditText(requireContext()).apply {
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            hint = if (defaultValue.isEmpty()) getString(R.string.mavlink_blank_auto_hint) else null
            setText(current)
            selectAll()
        }
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim()
                val port = value.toIntOrNull()
                if (numeric && (port == null || port !in numericRange)) {
                    Toast.makeText(requireContext(), invalidNumericMessage, Toast.LENGTH_SHORT).show()
                } else if (!numeric && value.isNotEmpty() && !isValidIpv4(value)) {
                    Toast.makeText(requireContext(), R.string.mavlink_invalid_ip, Toast.LENGTH_SHORT).show()
                } else {
                    preferences.edit { putString(key, value) }
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun refreshSummaries() {
        if (!::interfaceSummary.isInitialized) return
        interfaceSummary.text = preferences.getString(getString(R.string.mavlink_interface_pref), "UDP") ?: "UDP"
        localPortSummary.text = preferences.getString(getString(R.string.mavlink_lan_port_pref), "14550") ?: "14550"
        targetHostSummary.text = preferenceHost(R.string.mavlink_target_host_pref)
        targetPortSummary.text = preferences.getString(getString(R.string.mavlink_target_port_pref), "14550") ?: "14550"
        gcsSystemIdSummary.text = preferences.getString(getString(R.string.mavlink_gcs_system_id_pref), "254") ?: "254"
        qgcHostSummary.text = preferenceHost(R.string.mavlink_bridge_host_pref)
        qgcPortSummary.text = preferences.getString(getString(R.string.mavlink_bridge_port_pref), "14550") ?: "14550"

        val enabled = preferences.getBoolean(getString(R.string.mavlink_bridge_enabled_pref), false)
        bridgeSwitch.setOnCheckedChangeListener(null)
        bridgeSwitch.isChecked = enabled
        bridgeSwitch.setOnCheckedChangeListener { _, checked ->
            preferences.edit { putBoolean(getString(R.string.mavlink_bridge_enabled_pref), checked) }
            refreshSummaries()
        }
        val target = preferences.getString(getString(R.string.mavlink_target_host_pref), "").orEmpty().trim()
        val qgc = preferences.getString(getString(R.string.mavlink_bridge_host_pref), "").orEmpty().trim()
        bridgeStatus.text = when {
            !enabled -> getString(R.string.mavlink_bridge_status_off)
            target.isEmpty() || qgc.isEmpty() -> getString(R.string.mavlink_bridge_status_incomplete)
            target == qgc -> getString(R.string.mavlink_bridge_status_same_host)
            else -> getString(R.string.mavlink_bridge_status_ready, target, qgc)
        }
    }

    private fun preferenceHost(keyRes: Int): String = preferences.getString(getString(keyRes), "")
        ?.trim()?.takeIf(String::isNotEmpty) ?: getString(R.string.mavlink_auto_value)

    private fun isValidIpv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } && part.toInt() in 0..255
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (isAdded) refreshSummaries()
    }

    override fun onStart() {
        super.onStart()
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    companion object {
        private const val SUMMARY_TAG = "summary"
    }
}
