package com.example.screensender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenStreamService : Service() {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        private const val CHANNEL_ID = "screen_sender"
        private const val NOTIF_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val running = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground("Idle")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            running.set(false)
            stopForegroundCompat()
            releaseWakeLock()
            stopSelf()
            return START_NOT_STICKY
        }

        if (running.get()) return START_STICKY

        running.set(true)
        acquireWakeLock()

        Thread { streamLoop(intent) }.start()
        return START_STICKY
    }

    private fun streamLoop(intent: Intent?) {
        var socket: Socket? = null
        var projection: MediaProjection? = null
        var codec: MediaCodec? = null

        try {
            if (intent == null) throw IllegalStateException("No intent")

            val code = intent.getIntExtra("code", -1)
            val data = intent.getParcelableExtra<Intent>("data")
            val ip = intent.getStringExtra("ip") ?: throw IllegalStateException("Missing IP")
            val port = intent.getIntExtra("port", 9999)
            val bitrate = intent.getIntExtra("bitrate", 1_500_000)

            if (code == -1 || data == null) throw IllegalStateException("Missing projection permission data")

            // ✅ CONNECT FIRST so PC immediately sees a connection attempt
            updateForeground("Connecting to $ip:$port")
            socket = Socket(ip, port).apply { tcpNoDelay = true }
            val out = socket.getOutputStream()
            updateForeground("Connected. Starting capture…")

            // Now start projection + encoder
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mpm.getMediaProjection(code, data)

            val width = 960
            val height = 540
            val dpi = resources.displayMetrics.densityDpi

            val format = MediaFormat.createVideoFormat("video/avc", width, height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 20)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            codec = MediaCodec.createEncoderByType("video/avc")
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()

            projection.createVirtualDisplay("screen", width, height, dpi, 0, surface, null, null)
            updateForeground("Streaming $width x $height @ ${bitrate}bps")

            val info = MediaCodec.BufferInfo()

            while (running.get()) {
                val index = codec.dequeueOutputBuffer(info, 10_000)
                if (index >= 0) {
                    val buffer: ByteBuffer? = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        val bytes = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.get(bytes)
                        out.write(bytes)
                    }
                    codec.releaseOutputBuffer(index, false)
                }
            }
        } catch (e: Throwable) {
            // ✅ Show the real reason on the phone notification
            updateForeground("ERROR: ${e.javaClass.simpleName}: ${e.message ?: "?"}")
        } finally {
            running.set(false)
            try { socket?.close() } catch (_: Throwable) {}
            try { codec?.stop(); codec?.release() } catch (_: Throwable) {}
            try { projection?.stop() } catch (_: Throwable) {}
            stopForegroundCompat()
            releaseWakeLock()
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScreenSender::Lock")
            wakeLock?.acquire()
        } catch (_: Throwable) {}
    }

    private fun releaseWakeLock() {
        try { wakeLock?.release() } catch (_: Throwable) {}
        wakeLock = null
    }

    private fun startAsForeground(text: String) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ScreenSender", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(NOTIF_ID, buildNotification(text))
    }

    private fun updateForeground(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(text))
        } catch (_: Throwable) {}
    }

    private fun buildNotification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle("ScreenSender")
                .setContentText(text)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle("ScreenSender")
                .setContentText(text)
                .setOngoing(true)
                .build()
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
        } catch (_: Throwable) {}
    }
}
