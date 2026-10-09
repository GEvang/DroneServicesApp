package com.example.droneservicesapp.ui.geoawareness

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.FragmentGeoAwarenessBinding
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class GeoAwarenessFragment : Fragment() {
    private var _binding: FragmentGeoAwarenessBinding? = null
    private val binding get() = requireNotNull(_binding)

    private lateinit var activityViewModel: MainActivityViewModel
    private lateinit var droneViewModel: DroneViewModel
    private val session = GeoAwarenessSessionState()
    private var controllers: GeoAwarenessControllerFactory.Components? = null
    private val datasetPicker = GeoAwarenessDatasetPickerController(this, session) { selection ->
        controllers?.handlePickerSelection(selection)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentGeoAwarenessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<android.widget.ImageView>(R.id.more_header_icon)
            .setImageResource(R.drawable.ic_status_gps_pin_24)
        view.findViewById<android.widget.TextView>(R.id.more_header_title)
            .setText(R.string.geo_awareness_title)
        view.findViewById<android.widget.TextView>(R.id.more_header_subtitle)
            .setText(R.string.geo_awareness_subtitle)
        requireActivity().findViewById<View>(R.id.bottom_nav_view)?.isVisible = false

        activityViewModel = ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
        droneViewModel = ViewModelProvider(requireActivity())[DroneViewModel::class.java]
        controllers = GeoAwarenessControllerFactory().create(
            fragment = this,
            binding = binding,
            owner = viewLifecycleOwner,
            activityViewModel = activityViewModel,
            droneViewModel = droneViewModel,
            session = session,
            picker = datasetPicker,
        )
    }

    override fun onResume() {
        super.onResume()
        controllers?.onResume()
    }

    override fun onDestroyView() {
        controllers?.clear()
        controllers = null
        super.onDestroyView()
        _binding = null
    }
}
