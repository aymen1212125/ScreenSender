package com.example.screensender

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var mpm: MediaProjectionManager
    private lateinit var ipEdit: EditText
    private lateinit var portEdit: EditText
    private lateinit var bitrateEdit: EditText
    private lateinit var statusText: TextView

    private var pendingIp = ""
    private var pendingPort = 9999
    private var pendingBitrate = 1_500_000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        ipEdit = findViewById(R.id.ipEdit)
        portEdit = findViewById(R.id.portEdit)
        bitrateEdit = findViewById(R.id.bitrateEdit)
        statusText = findViewById(R.id.statusText)

        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        ipEdit.setText(prefs.getString("ip", "192.168.100.225") ?: "192.168.100.225")
        portEdit.setText(prefs.getInt("port", 9999).toString())
        bitrateEdit.setText(prefs.getInt("bitrate", 1_500_000).toString())

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            val ip = ipEdit.text.toString().trim()
            val port = portEdit.text.toString().trim().toIntOrNull()
            val bitrate = bitrateEdit.text.toString().trim().toIntOrNull()

            if (ip.isEmpty() || port == null || port !in 1..65535 || bitrate == null || bitrate < 100_000) {
                Toast.makeText(this, "Check IP/Port/Bitrate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save for next time
            prefs.edit()
                .putString("ip", ip)
                .putInt("port", port)
                .putInt("bitrate", bitrate)
                .apply()

            pendingIp = ip
            pendingPort = port
            pendingBitrate = bitrate

            statusText.text = "Status: requesting capture permission…"
            startActivityForResult(mpm.createScreenCaptureIntent(), 1000)
        }

        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            val i = Intent(this, ScreenStreamService::class.java)
            i.action = ScreenStreamService.ACTION_STOP
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            statusText.text = "Status: stopping…"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1000 && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, ScreenStreamService::class.java)
            i.action = ScreenStreamService.ACTION_START
            i.putExtra("code", resultCode)
            i.putExtra("data", data)
            i.putExtra("ip", pendingIp)
            i.putExtra("port", pendingPort)
            i.putExtra("bitrate", pendingBitrate)

            // CRITICAL for Android 8.1:
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)

            statusText.text = "Status: streaming to $pendingIp:$pendingPort"
            Toast.makeText(this, "Streaming to $pendingIp:$pendingPort", Toast.LENGTH_SHORT).show()
        } else {
            statusText.text = "Status: idle"
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}
