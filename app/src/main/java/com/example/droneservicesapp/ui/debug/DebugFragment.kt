package com.example.droneservicesapp.ui.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.FragmentDebugBinding
import com.example.droneservicesapp.mavserver.DroneViewModel

class DebugFragment : Fragment() {

    private var _binding: FragmentDebugBinding? = null
    private val binding get() = _binding!!
    private lateinit var droneViewModel: DroneViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDebugBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().findViewById<View>(R.id.bottom_nav_view)?.isVisible = false
        droneViewModel = ViewModelProvider(requireActivity())[DroneViewModel::class.java]

        bindSprayerButton(binding.debugSprayer1000Button, 1000)
        bindSprayerButton(binding.debugSprayer1500Button, 1500)
        bindSprayerButton(binding.debugSprayer2000Button, 2000)
        bindSprayerButton(binding.debugSprayer2200Button, 2200)

        droneViewModel.servo5OutputRaw.observe(viewLifecycleOwner) { pwm ->
            binding.debugSprayerDroneValue.text = if (pwm == null) {
                getString(R.string.debug_sprayer_drone_value_unknown)
            } else {
                getString(R.string.debug_sprayer_drone_value, pwm)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindSprayerButton(button: Button, pwm: Int) {
        button.setOnClickListener {
            val sent = droneViewModel.sendDebugSprayerServoPwm(pwm)
            val message = if (sent) {
                getString(R.string.debug_sprayer_command_sent, pwm)
            } else {
                getString(R.string.no_conn_msg)
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }
}
