package com.example.screensender

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DevicesFragment : Fragment(), NetworkRepository.Listener {

    private lateinit var ipEdit: EditText
    private lateinit var portEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var adapter: DeviceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_devices, container, false)
        ipEdit = view.findViewById(R.id.manualIpEdit)
        portEdit = view.findViewById(R.id.manualPortEdit)
        statusText = view.findViewById(R.id.controlStatusText)

        val prefs = requireContext().getSharedPreferences("cfg", Context.MODE_PRIVATE)
        ipEdit.setText(prefs.getString("ip", "") ?: "")
        portEdit.setText(prefs.getInt("control_port", NetworkConfig.DEFAULT_CONTROL_PORT).toString())

        view.findViewById<Button>(R.id.manualConnectBtn).setOnClickListener {
            val ip = ipEdit.text.toString().trim()
            val port = portEdit.text.toString().trim().toIntOrNull()
            if (ip.isEmpty() || port == null || port !in 1..65535) {
                Toast.makeText(requireContext(), "Enter a valid IP and port", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("ip", ip).putInt("control_port", port).apply()
            NetworkRepository.connectControl(requireContext(), ip, port)
        }

        view.findViewById<Button>(R.id.manualDisconnectBtn).setOnClickListener {
            NetworkRepository.disconnectControl()
        }

        val list = view.findViewById<RecyclerView>(R.id.devicesRecycler)
        adapter = DeviceAdapter { device ->
            ipEdit.setText(device.ip)
            portEdit.setText(device.controlPort.toString())
            NetworkRepository.connectControl(requireContext(), device.ip, device.controlPort)
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        return view
    }

    override fun onStart() {
        super.onStart()
        NetworkRepository.addListener(this)
        NetworkRepository.startDiscovery()
    }

    override fun onStop() {
        super.onStop()
        NetworkRepository.removeListener(this)
    }

    override fun onDevicesUpdated(devices: List<DeviceInfo>) {
        activity?.runOnUiThread {
            adapter.submitList(devices)
        }
    }

    override fun onControlStatus(status: ControlStatus, message: String?) {
        activity?.runOnUiThread {
            statusText.text =
                "Control: ${status.name.lowercase()}${if (message != null) " ($message)" else ""}"
        }
    }
}
