package com.example.screensender

import android.content.Context
import android.provider.Settings
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

data class DeviceInfo(
    val id: String,
    val name: String,
    val ip: String,
    val controlPort: Int,
    val lastSeen: Long
)

enum class ControlStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

object NetworkRepository {

    interface Listener {
        fun onDevicesUpdated(devices: List<DeviceInfo>)
        fun onControlStatus(status: ControlStatus, message: String?)
    }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val devices = ConcurrentHashMap<String, DeviceInfo>()
    private val discoveryRunning = AtomicBoolean(false)
    private val controlRunning = AtomicBoolean(false)
    private var discoveryThread: Thread? = null
    private var discoverySocket: DatagramSocket? = null
    private var controlThread: Thread? = null

    fun addListener(listener: Listener) {
        listeners.add(listener)
        listener.onDevicesUpdated(devices.values.sortedByDescending { it.lastSeen })
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun startDiscovery() {
        if (discoveryRunning.getAndSet(true)) return
        discoveryThread = thread(name = "discovery") {
            val buffer = ByteArray(512)
            try {
                discoverySocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(NetworkConfig.DISCOVERY_PORT))
                }
                while (discoveryRunning.get()) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    discoverySocket?.receive(packet)
                    val payload = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val device = parseDiscoveryPayload(payload, packet.address.hostAddress)
                    if (device != null) {
                        devices[device.id] = device
                        notifyDevicesUpdated()
                    }
                    pruneStale()
                }
            } catch (_: Throwable) {
                // Best-effort discovery; UI will show manual connect option.
            } finally {
                try { discoverySocket?.close() } catch (_: Throwable) {}
                discoverySocket = null
                discoveryRunning.set(false)
            }
        }
    }

    fun stopDiscovery() {
        discoveryRunning.set(false)
        try { discoverySocket?.close() } catch (_: Throwable) {}
        discoveryThread?.interrupt()
        discoveryThread = null
    }

    fun connectControl(context: Context, ip: String, port: Int) {
        if (controlRunning.getAndSet(true)) return
        controlThread = thread(name = "control") {
            var socket: Socket? = null
            try {
                notifyControlStatus(ControlStatus.CONNECTING, "Connecting to $ip:$port")
                socket = Socket(ip, port).apply { tcpNoDelay = true }
                val out = PrintWriter(socket.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))

                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: "android-unknown"
                val deviceName = android.os.Build.MODEL ?: "Android"
                out.println("HELLO|$deviceId|$deviceName|android|1")

                notifyControlStatus(ControlStatus.CONNECTED, "Connected")
                while (controlRunning.get()) {
                    val line = input.readLine() ?: break
                    if (line.startsWith("PING")) {
                        out.println("PONG")
                    }
                }
            } catch (e: Throwable) {
                notifyControlStatus(ControlStatus.ERROR, e.message ?: "Control error")
            } finally {
                controlRunning.set(false)
                try { socket?.close() } catch (_: Throwable) {}
                notifyControlStatus(ControlStatus.DISCONNECTED, "Disconnected")
            }
        }
    }

    fun disconnectControl() {
        controlRunning.set(false)
        controlThread?.interrupt()
        controlThread = null
    }

    private fun notifyDevicesUpdated() {
        val list = devices.values.sortedByDescending { it.lastSeen }
        for (listener in listeners) {
            listener.onDevicesUpdated(list)
        }
    }

    private fun notifyControlStatus(status: ControlStatus, message: String?) {
        for (listener in listeners) {
            listener.onControlStatus(status, message)
        }
    }

    private fun pruneStale() {
        val now = System.currentTimeMillis()
        val staleKeys = devices.values.filter { now - it.lastSeen > 15_000 }.map { it.id }
        if (staleKeys.isNotEmpty()) {
            for (key in staleKeys) devices.remove(key)
            notifyDevicesUpdated()
        }
    }

    private fun parseDiscoveryPayload(payload: String, ip: String): DeviceInfo? {
        val parts = payload.split('|')
        if (parts.size < 5) return null
        if (parts[0] != "SCREENSENDER" || parts[1] != "DISCOVERY") return null
        val id = parts[2]
        val name = parts[3]
        val port = parts[4].toIntOrNull() ?: return null
        return DeviceInfo(id, name, ip, port, System.currentTimeMillis())
    }
}
