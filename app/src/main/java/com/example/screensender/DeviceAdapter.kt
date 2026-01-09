package com.example.screensender

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val onConnect: (DeviceInfo) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<DeviceInfo>()

    fun submitList(list: List<DeviceInfo>) {
        devices.clear()
        devices.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view, onConnect)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(
        itemView: View,
        private val onConnect: (DeviceInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val nameText: TextView = itemView.findViewById(R.id.deviceName)
        private val detailText: TextView = itemView.findViewById(R.id.deviceDetail)
        private val connectBtn: Button = itemView.findViewById(R.id.deviceConnectBtn)

        fun bind(device: DeviceInfo) {
            nameText.text = device.name
            detailText.text = "${device.ip}:${device.controlPort}"
            connectBtn.setOnClickListener { onConnect(device) }
        }
    }
}
