package com.example.droneservicesapp.data.rtk

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.shell.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RtkKeepAliveForegroundService : Service() {

    companion object {
        private const val TAG = "RtkForwarding"
        private const val CHANNEL_ID = "rtk_keep_alive"
        private const val CHANNEL_NAME = "RTK keep alive"
        private const val NOTIFICATION_ID = 4101
        private const val WAKE_LOCK_TAG = "DroneServicesApp:RtkForwarding"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WAKE_LOCK_RENEW_MS = 5 * 60 * 1000L

        private const val ACTION_START_SESSION =
            "com.example.droneservicesapp.action.START_RTK_KEEPALIVE"
        private const val ACTION_SET_WAKE_ACTIVE =
            "com.example.droneservicesapp.action.SET_RTK_WAKE_ACTIVE"
        private const val ACTION_STOP_SESSION =
            "com.example.droneservicesapp.action.STOP_RTK_KEEPALIVE"
        private const val EXTRA_WAKE_ACTIVE = "extra_wake_active"

        fun startSession(context: Context) {
            val intent = Intent(context, RtkKeepAliveForegroundService::class.java).apply {
                action = ACTION_START_SESSION
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun setWakeActive(context: Context, active: Boolean) {
            val intent = Intent(context, RtkKeepAliveForegroundService::class.java).apply {
                action = ACTION_SET_WAKE_ACTIVE
                putExtra(EXTRA_WAKE_ACTIVE, active)
            }
            if (active) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopSession(context: Context) {
            val intent = Intent(context, RtkKeepAliveForegroundService::class.java).apply {
                action = ACTION_STOP_SESSION
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeActive = false
    private var foregroundStarted = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeRenewalJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> {
                ensureForeground()
            }

            ACTION_SET_WAKE_ACTIVE -> {
                ensureForeground()
                setWakeActiveInternal(intent.getBooleanExtra(EXTRA_WAKE_ACTIVE, false))
            }

            ACTION_STOP_SESSION -> {
                releaseWakeLockIfHeld("session stopping")
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
                stopSelf()
            }

            else -> ensureForeground()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        wakeRenewalJob?.cancel()
        serviceScope.cancel()
        releaseWakeLockIfHeld("service destroyed")
        foregroundStarted = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureForeground() {
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, buildNotification())
            foregroundStarted = true
        } else {
            updateNotification()
        }
    }

    private fun setWakeActiveInternal(active: Boolean) {
        if (wakeActive == active) {
            if (!active) {
                updateNotification()
            }
            return
        }
        wakeActive = active
        if (active) {
            acquireWakeLock()
            ensureWakeRenewal()
        } else {
            wakeRenewalJob?.cancel()
            wakeRenewalJob = null
            releaseWakeLockIfHeld("inactive streaming state")
        }
        updateNotification()
    }

    private fun ensureWakeRenewal() {
        if (wakeRenewalJob?.isActive == true) return
        wakeRenewalJob = serviceScope.launch {
            while (isActive && wakeActive) {
                delay(WAKE_LOCK_RENEW_MS)
                if (!wakeActive) break
                acquireWakeLock()
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        val interactive = pm?.isInteractive
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        Log.i(TAG, "wake lock acquired interactive=$interactive timeoutMs=$WAKE_LOCK_TIMEOUT_MS")
        if (interactive == false) {
            Log.i(TAG, "service entering streaming while screen off or device locked")
        }
    }

    private fun releaseWakeLockIfHeld(reason: String) {
        wakeActive = false
        wakeRenewalJob?.cancel()
        wakeRenewalJob = null
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.i(TAG, "wake lock released reason=$reason")
        }
        updateNotification()
    }

    private fun updateNotification() {
        if (!foregroundStarted) return
        if (!canPostNotifications()) return
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (wakeActive) {
            getString(R.string.rtk_keep_alive_notification_active)
        } else {
            getString(R.string.rtk_keep_alive_notification_idle)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.drone_services_square)
            .setContentTitle(getString(R.string.rtk_keep_alive_notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }
}
