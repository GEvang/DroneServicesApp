package com.example.droneservicesapp.ui.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.MotionEvent
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.databinding.FragmentDebugBinding
import com.example.droneservicesapp.mavserver.DroneLogDownloadState
import com.example.droneservicesapp.mavserver.DroneLogFile
import com.example.droneservicesapp.mavserver.DroneLogRequestResult
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.VehicleParameterCatalogState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class DebugFragment : Fragment() {
    companion object {
        // Temporary access code requested for the field build. Replace with managed auth before release.
        private const val DEFAULT_DEBUG_PIN = "123"
        private const val DEBUG_PREFS = "debug_access"
        private const val KEY_ACCESS_ENABLED = "access_enabled"
        private const val KEY_ACCESS_CODE = "access_code"
    }

    private var _binding: FragmentDebugBinding? = null
    private val binding get() = _binding!!
    private lateinit var droneViewModel: DroneViewModel
    private lateinit var parameterAdapter: VehicleParameterAdapter
    private var displayedLogs: List<DroneLogFile> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDebugBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().findViewById<View>(R.id.bottom_nav_view)?.isVisible = false
        droneViewModel = ViewModelProvider(requireActivity())[DroneViewModel::class.java]
        parameterAdapter = VehicleParameterAdapter(::showParameterEditor)
        bindUnlockGate()
        bindDebugControls()
        bindDebugAccessSettings()
        bindParameterCatalog()
        bindLogTransfer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindUnlockGate() {
        if (!debugPreferences().getBoolean(KEY_ACCESS_ENABLED, true)) {
            binding.debugUnlockContainer.isVisible = false
            binding.debugContentScroll.isVisible = true
            return
        }
        fun unlock() {
            val savedCode = debugPreferences().getString(KEY_ACCESS_CODE, DEFAULT_DEBUG_PIN) ?: DEFAULT_DEBUG_PIN
            if (binding.debugPinInput.text?.toString() == savedCode) {
                binding.debugPinError.isVisible = false
                binding.debugUnlockContainer.isVisible = false
                binding.debugContentScroll.isVisible = true
                binding.debugPinInput.text?.clear()
            } else {
                binding.debugPinError.isVisible = true
                binding.debugPinInput.selectAll()
            }
        }
        binding.debugUnlockButton.setOnClickListener { unlock() }
        bindKeyboardInput(binding.debugPinInput)
        binding.debugPinInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { unlock(); true } else false
        }
    }

    private fun bindDebugAccessSettings() {
        val preferences = debugPreferences()
        binding.debugAccessEnabledSwitch.isChecked = preferences.getBoolean(KEY_ACCESS_ENABLED, true)
        binding.debugAccessEnabledSwitch.setOnCheckedChangeListener { _, enabled ->
            preferences.edit().putBoolean(KEY_ACCESS_ENABLED, enabled).apply()
            toast(if (enabled) R.string.debug_access_enabled_notice else R.string.debug_access_disabled_notice)
        }
        binding.debugSaveAccessCodeButton.setOnClickListener {
            val code = binding.debugAccessCodeInput.text?.toString().orEmpty()
            if (code.isBlank()) {
                binding.debugAccessCodeInput.error = getString(R.string.debug_access_code_required)
                showKeyboard(binding.debugAccessCodeInput)
            } else {
                preferences.edit().putString(KEY_ACCESS_CODE, code).apply()
                binding.debugAccessCodeInput.text?.clear()
                toast(R.string.debug_access_code_saved)
            }
        }
        bindKeyboardInput(binding.debugAccessCodeInput)
    }

    private fun debugPreferences() = requireContext().getSharedPreferences(DEBUG_PREFS, 0)

    private fun bindKeyboardInput(input: EditText) {
        input.showSoftInputOnFocus = true
        input.isFocusableInTouchMode = true
        input.setOnClickListener { showKeyboard(input) }
        input.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) showKeyboard(input)
            false
        }
        input.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showKeyboard(input) }
    }

    private fun showKeyboard(view: View) {
        view.isFocusableInTouchMode = true
        view.requestFocusFromTouch()
        requireActivity().window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        val show: () -> Unit = {
            val keyboard = requireContext().getSystemService(InputMethodManager::class.java)
            keyboard?.restartInput(view)
            keyboard?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            ViewCompat.getWindowInsetsController(view)?.show(WindowInsetsCompat.Type.ime())
            Unit
        }
        view.post(show)
        view.postDelayed(show, 180L)
    }

    private fun bindDebugControls() {
        bindSprayerButton(binding.debugSprayer1000Button, 1000)
        bindSprayerButton(binding.debugSprayer1500Button, 1500)
        bindSprayerButton(binding.debugSprayer2000Button, 2000)
        bindSprayerButton(binding.debugSprayer2200Button, 2200)
        binding.debugExportDiagnosticsButton.setOnClickListener { exportDiagnostics() }
        binding.debugMarkIncidentButton.setOnClickListener {
            droneViewModel.markDiagnosticIncident()
            binding.debugDiagnosticsStatus.text = getString(R.string.debug_diagnostics_incident_marked)
        }
        droneViewModel.servo5OutputRaw.observe(viewLifecycleOwner) { pwm ->
            binding.debugSprayerDroneValue.text = if (pwm == null) {
                getString(R.string.debug_sprayer_drone_value_unknown)
            } else {
                getString(R.string.debug_sprayer_drone_value, pwm)
            }
        }
    }

    private fun bindParameterCatalog() {
        binding.debugParameterList.layoutManager = LinearLayoutManager(requireContext())
        binding.debugParameterList.adapter = parameterAdapter
        bindKeyboardInput(binding.debugParameterSearch)
        binding.debugParameterSearch.doAfterTextChanged { parameterAdapter.filter(it?.toString().orEmpty()) }
        binding.debugParameterRefreshButton.setOnClickListener {
            if (!droneViewModel.refreshVehicleParameterCatalog()) toast(R.string.debug_requires_connection)
        }
        droneViewModel.vehicleParameterCatalog.observe(viewLifecycleOwner) { state ->
            parameterAdapter.submit(state.parameters)
            renderParameterState(state)
        }
    }

    private fun showParameterEditor(parameter: com.example.droneservicesapp.mavserver.VehicleParameter) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(parameter.value.toString())
            selectAll()
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(getString(R.string.debug_parameter_edit_title, parameter.name))
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.debug_parameter_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text?.toString()?.toFloatOrNull()
                when {
                    value == null || !value.isFinite() -> input.error = getString(R.string.debug_parameter_invalid_value)
                    !droneViewModel.setVehicleParameter(parameter.name, value) -> toast(R.string.debug_requires_connection)
                    else -> {
                        toast(R.string.debug_parameter_write_sent)
                        dialog.dismiss()
                    }
                }
            }
            showKeyboard(input)
        }
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }

    private fun renderParameterState(state: VehicleParameterCatalogState) {
        binding.debugParameterProgress.isVisible = state.loading
        val expected = state.expectedCount
        binding.debugParameterProgress.isIndeterminate = state.loading && expected == null
        if (expected != null && expected > 0) {
            binding.debugParameterProgress.progress = (state.receivedCount * 100f / expected).roundToInt().coerceIn(0, 100)
        }
        binding.debugParameterRefreshButton.isEnabled = !state.loading
        binding.debugParameterStatus.text = when {
            state.error != null -> state.error
            state.loading && expected != null -> getString(R.string.debug_parameters_loading_count, state.receivedCount, expected)
            state.loading -> getString(R.string.debug_parameters_loading)
            state.partial -> getString(R.string.debug_parameters_partial, state.receivedCount, expected ?: state.receivedCount)
            state.parameters.isNotEmpty() -> getString(R.string.debug_parameters_loaded, state.parameters.size)
            else -> getString(R.string.debug_parameters_not_loaded)
        }
    }

    private fun bindLogTransfer() {
        binding.debugLogRefreshButton.setOnClickListener {
            presentLogRequestResult(droneViewModel.refreshDroneLogs())
        }
        binding.debugLogDownloadButton.setOnClickListener {
            val selected = displayedLogs.getOrNull(binding.debugLogSpinner.selectedItemPosition)
            if (selected == null) toast(R.string.debug_dataflash_select_log)
            else presentLogRequestResult(droneViewModel.downloadDroneLog(selected.id))
        }
        droneViewModel.droneLogCatalog.observe(viewLifecycleOwner) { state ->
            displayedLogs = state.logs
            val labels = state.logs.map(::formatLog)
            binding.debugLogSpinner.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_item, labels
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            binding.debugLogRefreshButton.isEnabled = !state.loading
            binding.debugLogDownloadButton.isEnabled = state.logs.isNotEmpty() &&
                droneViewModel.droneLogDownloadState.value !is DroneLogDownloadState.Downloading
            binding.debugLogStatus.text = when {
                state.loading -> getString(R.string.debug_dataflash_loading)
                state.error != null -> state.error
                state.logs.isNotEmpty() -> getString(R.string.debug_dataflash_loaded, state.logs.size)
                else -> getString(R.string.debug_dataflash_not_loaded)
            }
        }
        droneViewModel.droneLogDownloadState.observe(viewLifecycleOwner) { state ->
            val downloading = state is DroneLogDownloadState.Downloading
            binding.debugLogProgress.isVisible = downloading
            binding.debugLogDownloadButton.isEnabled = !downloading && displayedLogs.isNotEmpty()
            binding.debugLogRefreshButton.isEnabled = !downloading
            when (state) {
                DroneLogDownloadState.Idle -> Unit
                is DroneLogDownloadState.Downloading -> {
                    binding.debugLogProgress.progress = if (state.totalBytes > 0) {
                        (state.receivedBytes * 100.0 / state.totalBytes).roundToInt().coerceIn(0, 100)
                    } else 0
                    binding.debugLogStatus.text = getString(
                        R.string.debug_dataflash_downloading,
                        formatBytes(state.receivedBytes),
                        formatBytes(state.totalBytes),
                    )
                }
                is DroneLogDownloadState.Succeeded -> {
                    binding.debugLogStatus.text = getString(R.string.debug_dataflash_saved, state.location)
                    droneViewModel.clearDroneLogDownloadResult()
                }
                is DroneLogDownloadState.Failed -> {
                    binding.debugLogStatus.text = getString(R.string.debug_dataflash_failed, state.reason)
                    droneViewModel.clearDroneLogDownloadResult()
                }
            }
        }
    }

    private fun presentLogRequestResult(result: DroneLogRequestResult) {
        when (result) {
            DroneLogRequestResult.SENT -> Unit
            DroneLogRequestResult.DISCONNECTED -> toast(R.string.debug_requires_connection)
            DroneLogRequestResult.ARMED -> toast(R.string.debug_dataflash_disarm_first)
            DroneLogRequestResult.BUSY -> toast(R.string.debug_dataflash_busy)
            DroneLogRequestResult.INVALID_LOG -> toast(R.string.debug_dataflash_select_log)
        }
    }

    private fun formatLog(log: DroneLogFile): String {
        val date = if (log.timeUtcSeconds > 0) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(log.timeUtcSeconds * 1000L))
        } else getString(R.string.debug_dataflash_unknown_date)
        return getString(R.string.debug_dataflash_log_item, log.id, formatBytes(log.sizeBytes), date)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun bindSprayerButton(button: Button, pwm: Int) {
        button.setOnClickListener {
            val message = if (droneViewModel.sendDebugSprayerServoPwm(pwm)) {
                getString(R.string.debug_sprayer_command_sent, pwm)
            } else getString(R.string.no_conn_msg)
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun exportDiagnostics() {
        val context = requireContext().applicationContext
        binding.debugExportDiagnosticsButton.isEnabled = false
        binding.debugDiagnosticsStatus.text = getString(R.string.debug_diagnostics_exporting)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = DiagnosticLog.export(context)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.debugExportDiagnosticsButton.isEnabled = true
                binding.debugDiagnosticsStatus.text = when (result) {
                    is DiagnosticLog.ExportResult.Success -> getString(R.string.debug_diagnostics_exported, result.location)
                    is DiagnosticLog.ExportResult.Failure -> getString(R.string.debug_diagnostics_export_failed, result.reason)
                }
            }
        }
    }

    private fun toast(message: Int) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}
