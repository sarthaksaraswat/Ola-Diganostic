package com.sarthak.olacan.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.sarthak.olacan.MainActivity
import com.sarthak.olacan.util.AppLogManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CanStreamService : Service() {

    companion object {
        const val CHANNEL_ID = "ola_can_stream_channel"
        const val NOTIFICATION_ID = 50005
        const val ACTION_START = "com.sarthak.olacan.ACTION_START"
        const val ACTION_STOP = "com.sarthak.olacan.ACTION_STOP"
        const val ACTION_STOP_RECORDING = "com.sarthak.olacan.ACTION_STOP_RECORDING"
    }

    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        AppLogManager.error("CanStreamService", "Service coroutine error: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + serviceExceptionHandler)
    private var updateJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // CRITICAL: Promote to foreground immediately in onCreate() within <50ms to satisfy Android's 5s deadline!
        startForegroundServiceCompat()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Only disconnect if still connected to avoid recursion loop
                if (CanStreamManager.streamStats.value.isConnected || CanStreamManager.isDemoMode.value) {
                    CanStreamManager.disconnect()
                }
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (_: Exception) {}
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_RECORDING -> {
                CanStreamManager.stopRecording(applicationContext)
                try {
                    val notif = buildNotification()
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notif)
                } catch (_: Exception) {}
            }
            else -> {
                startForegroundServiceCompat()
                startNotificationLoop()
            }
        }
        // Use START_NOT_STICKY so Android OS does NOT reboot this background telemetry socket if app was closed
        return START_NOT_STICKY
    }

    private fun startForegroundServiceCompat() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // In Android 14+ (API 34/35), use combined types matching AndroidManifest.xml
                val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                startForeground(NOTIFICATION_ID, notification, type)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            AppLogManager.warn("CanStreamService", "startForeground combined failed: ${e.message}, retrying fallback")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e2: Exception) {
                AppLogManager.error("CanStreamService", "Fallback startForeground failed: ${e2.message}", e2)
            }
        }
    }

    private fun startNotificationLoop() {
        updateJob?.cancel()
        updateJob = scope.launch {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            while (isActive) {
                val isRec = CanStreamManager.isRecording.value
                val isConn = CanStreamManager.streamStats.value.isConnected
                val interval = when {
                    isRec -> 2500L // Responsive timer for active recording
                    isConn -> 5000L // Low-frequency refresh for live stream to minimize IPC wakeups
                    else -> 8000L // Minimal wakeups when idle
                }
                delay(interval)
                try {
                    val notif = buildNotification()
                    notificationManager.notify(NOTIFICATION_ID, notif)
                } catch (_: Exception) {}
            }
        }
    }

    private fun buildNotification(): Notification {
        val stats = CanStreamManager.streamStats.value
        val isRec = CanStreamManager.isRecording.value
        val recDuration = CanStreamManager.recordingDurationSec.value
        val recFrames = CanStreamManager.recordedFramesCount.value
        val recSize = CanStreamManager.recordedSizeFormatted.value
        val recAlerts = CanStreamManager.recordedAlertsCount.value
        val bgRemaining = CanStreamManager.backgroundTimerRemainingSec.value

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when {
            isRec -> {
                val mins = recDuration / 60
                val secs = recDuration % 60
                String.format(java.util.Locale.US, "● Recording CAN (%02d:%02d) - %s", mins, secs, recSize)
            }
            stats.isConnected -> "Ola Diagnostic Active"
            else -> "Ola Diagnostic (Idle)"
        }

        val text = buildString {
            if (stats.isConnected) {
                append("${stats.connectedHost} (${String.format(java.util.Locale.US, "%.0f", stats.framesPerSecond)} FPS)")
                if (isRec) {
                    append(" | Frames: $recFrames")
                    if (recAlerts > 0) append(" | Alerts: $recAlerts")
                }
            } else {
                append(stats.statusMessage)
            }

            if (bgRemaining != null && bgRemaining > 0) {
                val bMins = bgRemaining / 60
                val bSecs = bgRemaining % 60
                append(String.format(java.util.Locale.US, "\nAuto-stop in %02d:%02d", bMins, bSecs))
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Action: Stop recording
        if (isRec) {
            val stopRecIntent = Intent(this, CanStreamService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            val pendingStopRec = PendingIntent.getService(
                this,
                1,
                stopRecIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Stop & Save", pendingStopRec)
        }

        // Action: Disconnect
        val disconnectIntent = Intent(this, CanStreamService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingDisconnect = PendingIntent.getService(
            this,
            2,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(android.R.drawable.ic_delete, "Disconnect", pendingDisconnect)

        return builder.build()
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OlaCanApp:CanStreamLock").apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L) // 6 hour max safety timeout for long rides
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "OlaCanApp:CanWifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null

            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ola Diagnostic Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps CAN bus stream and data recording alive when app is minimized"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        updateJob?.cancel()
        updateJob = null
        releaseLocks()
        super.onDestroy()
    }
}
