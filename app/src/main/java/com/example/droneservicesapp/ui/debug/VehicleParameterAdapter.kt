package com.example.droneservicesapp.ui.debug

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.VehicleParameter
import java.util.Locale

internal class VehicleParameterAdapter : ListAdapter<VehicleParameter, VehicleParameterAdapter.Holder>(DiffCallback) {
    private var allParameters: List<VehicleParameter> = emptyList()
    private var query: String = ""

    fun submit(parameters: List<VehicleParameter>) {
        allParameters = parameters
        rebuildVisible()
    }

    fun filter(value: String) {
        query = value.trim()
        rebuildVisible()
    }

    private fun rebuildVisible() {
        submitList(if (query.isBlank()) allParameters else {
            allParameters.filter { it.name.contains(query, ignoreCase = true) }
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_vehicle_parameter, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    private object DiffCallback : DiffUtil.ItemCallback<VehicleParameter>() {
        override fun areItemsTheSame(oldItem: VehicleParameter, newItem: VehicleParameter): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: VehicleParameter, newItem: VehicleParameter): Boolean =
            oldItem == newItem
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.parameter_name)
        private val value: TextView = view.findViewById(R.id.parameter_value)
        private val type: TextView = view.findViewById(R.id.parameter_type)

        fun bind(parameter: VehicleParameter) {
            name.text = parameter.name
            value.text = formatValue(parameter.value)
            type.text = parameter.type
        }

        private fun formatValue(value: Float): String {
            val integral = value.toLong()
            return if (value.isFinite() && value == integral.toFloat()) integral.toString()
            else String.format(Locale.US, "%.6g", value)
        }
    }
}
