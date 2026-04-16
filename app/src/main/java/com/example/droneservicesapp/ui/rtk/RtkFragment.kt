package com.example.droneservicesapp.ui.rtk

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.rtk.NtripClient
import com.example.droneservicesapp.data.rtk.NtripResult
import com.example.droneservicesapp.data.rtk.RtkConfig
import com.example.droneservicesapp.data.rtk.RtkPreferences
import com.example.droneservicesapp.data.rtk.RtkValidator
import com.example.droneservicesapp.databinding.FragmentRtkBinding
import kotlinx.coroutines.launch

class RtkFragment : Fragment() {

    private var _binding: FragmentRtkBinding? = null
    private val binding get() = _binding!!

    private lateinit var rtkPreferences: RtkPreferences
    private val ntripClient = NtripClient()
    private var isPopulatingForm = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRtkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rtkPreferences = RtkPreferences(requireContext())

        requireActivity().findViewById<View>(R.id.bottom_nav_view)?.isVisible = false
        binding.rtkMountpointInput.keyListener = null

        loadSavedValues()
        bindForm()

        binding.fetchMountpointsButton.setOnClickListener { fetchMountpoints() }
        binding.testConnectionButton.setOnClickListener { testConnection() }
    }

    private fun bindForm() {
        binding.rtkIpInput.doAfterTextChanged { text ->
            if (isPopulatingForm) return@doAfterTextChanged
            rtkPreferences.saveIp(text?.toString().orEmpty())
            onBaseConfigChanged()
        }
        binding.rtkPortInput.doAfterTextChanged { text ->
            if (isPopulatingForm) return@doAfterTextChanged
            text?.toString()?.trim()?.toIntOrNull()?.let(rtkPreferences::savePort)
            onBaseConfigChanged()
        }
        binding.rtkUsernameInput.doAfterTextChanged { text ->
            if (isPopulatingForm) return@doAfterTextChanged
            rtkPreferences.saveUsername(text?.toString().orEmpty())
            onBaseConfigChanged()
        }
        binding.rtkPasswordInput.doAfterTextChanged { text ->
            if (isPopulatingForm) return@doAfterTextChanged
            rtkPreferences.savePassword(text?.toString().orEmpty())
            onBaseConfigChanged()
        }
    }

    private fun loadSavedValues() {
        val config = rtkPreferences.getConfig()
        isPopulatingForm = true
        binding.rtkIpInput.setText(config.ip)
        binding.rtkPortInput.setText(config.port.toString())
        binding.rtkUsernameInput.setText(config.username)
        binding.rtkPasswordInput.setText(config.password)
        binding.rtkMountpointInput.setText(config.mountpoint)
        binding.rtkStatusValue.text = config.lastStatusMessage.ifBlank {
            getString(R.string.rtk_connection_not_tested)
        }
        isPopulatingForm = false
    }

    private fun fetchMountpoints() {
        val config = buildConfig(requireMountpoint = false) ?: return
        setBusyState(true)
        updateStatus(getString(R.string.rtk_status_fetching), false)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = ntripClient.fetchSourceTable(config)
            setBusyState(false)
            when (result) {
                is NtripResult.SourceTableSuccess -> handleMountpoints(result.mountpoints)
                else -> handleResult(result)
            }
        }
    }

    private fun testConnection() {
        val config = buildConfig(requireMountpoint = true) ?: return
        setBusyState(true)
        updateStatus(getString(R.string.rtk_status_testing), false)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = ntripClient.testConnection(config)
            setBusyState(false)
            handleResult(result, showToast = true)
        }
    }

    private fun handleMountpoints(mountpoints: List<String>) {
        if (mountpoints.isEmpty()) {
            val message = getString(R.string.rtk_status_no_mountpoints)
            updateStatus(message, false)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            return
        }

        if (mountpoints.size == 1) {
            selectMountpoint(mountpoints.first())
            return
        }

        val currentMountpoint = binding.rtkMountpointInput.text?.toString().orEmpty()
        val selectedIndex = mountpoints.indexOf(currentMountpoint).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rtk_select_mountpoint)
            .setSingleChoiceItems(mountpoints.toTypedArray(), selectedIndex) { dialog, which ->
                selectMountpoint(mountpoints[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun selectMountpoint(mountpoint: String) {
        isPopulatingForm = true
        binding.rtkMountpointInput.setText(mountpoint)
        isPopulatingForm = false
        rtkPreferences.saveMountpoint(mountpoint)
        val message = getString(R.string.rtk_mountpoint_selected, mountpoint)
        updateStatus(message, false)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun handleResult(result: NtripResult, showToast: Boolean = false) {
        val message = when (result) {
            is NtripResult.ConnectionSuccess -> getString(R.string.rtk_status_connected)
            is NtripResult.AuthFailure -> getString(R.string.rtk_status_auth_failed)
            is NtripResult.MountpointNotFound -> getString(R.string.rtk_status_mountpoint_not_found)
            is NtripResult.NetworkFailure -> getString(
                R.string.rtk_status_network_failed,
                result.message
            )
            is NtripResult.InvalidConfig -> getString(
                R.string.rtk_status_invalid_config,
                result.message
            )
            is NtripResult.ProtocolFailure -> result.message
            is NtripResult.SourceTableSuccess -> getString(R.string.rtk_status_idle)
        }

        updateStatus(
            message = message,
            success = result is NtripResult.ConnectionSuccess
        )

        if (showToast) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildConfig(requireMountpoint: Boolean): RtkConfig? {
        val ip = binding.rtkIpInput.text?.toString().orEmpty().trim()
        val port = binding.rtkPortInput.text?.toString()?.trim()?.toIntOrNull()
        val username = binding.rtkUsernameInput.text?.toString().orEmpty().trim()
        val password = binding.rtkPasswordInput.text?.toString().orEmpty()
        val mountpoint = binding.rtkMountpointInput.text?.toString().orEmpty().trim()

        val validationMessage = when {
            !RtkValidator.isValidIp(ip) -> getString(
                R.string.rtk_status_invalid_config,
                getString(R.string.rtk_ip_required)
            )
            port == null || !RtkValidator.isValidPort(port) -> getString(
                R.string.rtk_status_invalid_config,
                getString(R.string.rtk_port_invalid)
            )
            !RtkValidator.isValidUsername(username) -> getString(
                R.string.rtk_status_invalid_config,
                getString(R.string.rtk_username_required)
            )
            !RtkValidator.isValidPassword(password) -> getString(
                R.string.rtk_status_invalid_config,
                getString(R.string.rtk_password_required)
            )
            requireMountpoint && !RtkValidator.isValidMountpoint(mountpoint) -> getString(
                R.string.rtk_status_invalid_config,
                getString(R.string.rtk_mountpoint_required)
            )
            else -> null
        }

        if (validationMessage != null) {
            updateStatus(validationMessage, false)
            Toast.makeText(requireContext(), validationMessage, Toast.LENGTH_SHORT).show()
            return null
        }

        return RtkConfig(
            ip = ip,
            port = port ?: 0,
            username = username,
            password = password,
            mountpoint = mountpoint
        )
    }

    private fun updateStatus(message: String, success: Boolean) {
        binding.rtkStatusValue.text = message
        rtkPreferences.saveLastStatusMessage(message)
        rtkPreferences.saveLastFetchSucceeded(success)
    }

    private fun markConnectionNotTested() {
        if (_binding == null) return
        updateStatus(getString(R.string.rtk_connection_not_tested), false)
    }

    private fun onBaseConfigChanged() {
        clearMountpointSelection()
        markConnectionNotTested()
    }

    private fun clearMountpointSelection() {
        val currentMountpoint = binding.rtkMountpointInput.text?.toString().orEmpty()
        if (currentMountpoint.isEmpty()) return

        isPopulatingForm = true
        binding.rtkMountpointInput.setText("")
        isPopulatingForm = false
        rtkPreferences.saveMountpoint("")
    }

    private fun setBusyState(isBusy: Boolean) {
        binding.fetchMountpointsButton.isEnabled = !isBusy
        binding.testConnectionButton.isEnabled = !isBusy
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
