package com.example.droneservicesapp.ui.parameters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.FragmentParametersBinding
import com.example.droneservicesapp.mavserver.DroneParameterController
import com.example.droneservicesapp.mavserver.VehicleParameterAvailability
import com.example.droneservicesapp.mavserver.VehicleParameterResult
import com.example.droneservicesapp.mavserver.VehicleParameterUiState
import com.example.droneservicesapp.mavserver.DroneViewModel

class ParametersFragment : Fragment() {

    private var _binding: FragmentParametersBinding? = null
    private val binding get() = _binding!!
    private lateinit var droneViewModel: DroneViewModel
    private var rendering = false
    private var wasConnected: Boolean? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParametersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().findViewById<View>(R.id.bottom_nav_view)?.isVisible = false
        droneViewModel = ViewModelProvider(requireActivity())[DroneViewModel::class.java]

        binding.avoidanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!rendering) droneViewModel.setAvoidanceEnabled(isChecked)
        }
        binding.surfaceTrackingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!rendering) droneViewModel.setSurfaceTrackingEnabled(isChecked)
        }

        droneViewModel.avoidanceParameter.observe(viewLifecycleOwner) {
            renderAvoidance(it)
        }
        droneViewModel.surfaceTrackingParameter.observe(viewLifecycleOwner) {
            renderSurfaceTracking(it)
        }
        droneViewModel.parameterFeedback.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { feedback ->
                val label = when (feedback.parameterName) {
                    DroneParameterController.AVOID_ENABLE -> getString(R.string.parameter_avoidance_label)
                    else -> getString(R.string.parameter_surface_tracking_label)
                }
                val message = if (feedback.succeeded) {
                    getString(R.string.parameter_write_success, label)
                } else {
                    getString(R.string.parameter_write_error, label)
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }
        droneViewModel.conStateLiveData.observe(viewLifecycleOwner) { connected ->
            if (connected == true && wasConnected != true) {
                droneViewModel.refreshTerrainFollowingParameters()
            }
            wasConnected = connected == true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderAvoidance(state: VehicleParameterUiState) {
        rendering = true
        binding.avoidanceSwitch.isChecked = (state.value ?: 0) != 0
        binding.avoidanceSwitch.isEnabled =
            state.availability == VehicleParameterAvailability.SUPPORTED && !state.isWriting
        binding.avoidanceStatus.text = statusText(state)
        rendering = false
    }

    private fun renderSurfaceTracking(state: VehicleParameterUiState) {
        rendering = true
        binding.surfaceTrackingSwitch.isChecked = (state.value ?: 0) != 0
        binding.surfaceTrackingSwitch.isEnabled =
            state.availability == VehicleParameterAvailability.SUPPORTED && !state.isWriting
        binding.surfaceTrackingStatus.text = statusText(state)
        rendering = false
    }

    private fun statusText(state: VehicleParameterUiState): String = when {
        state.isWriting -> getString(R.string.parameter_status_writing)
        state.result == VehicleParameterResult.SUCCESS -> getString(R.string.parameter_status_saved)
        state.result == VehicleParameterResult.ERROR -> getString(R.string.parameter_status_write_error)
        state.availability == VehicleParameterAvailability.LOADING -> getString(R.string.parameter_status_loading)
        state.availability == VehicleParameterAvailability.UNSUPPORTED -> getString(R.string.parameter_not_supported)
        state.availability == VehicleParameterAvailability.DISCONNECTED -> getString(R.string.parameter_status_disconnected)
        else -> ""
    }
}
