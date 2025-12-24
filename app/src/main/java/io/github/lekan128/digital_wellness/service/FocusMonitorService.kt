package io.github.lekan128.digital_wellness.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.lekan128.digital_wellness.MainActivity
import io.github.lekan128.digital_wellness.R
import io.github.lekan128.digital_wellness.data.SettingsManager
import io.github.lekan128.digital_wellness.ui.overlay.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FocusMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    
    // Polling optimization: variables declared outside the loop
    private var currentPackage: String = ""
    private var lastPackage: String = ""
    private var currentUsageDuration: Long = 0L
    private var limitDurationMillis: Long = 20 * 60 * 1000L // Default 20 mins
    private var selectedApps: Set<String> = emptySet()
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var overlayManager: OverlayManager
    private lateinit var usageStatsManager: UsageStatsManager

    private val CHANNEL_ID = "FocusMonitorChannel"
    private val NOTIFICATION_ID = 1234

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> stopPolling()
                Intent.ACTION_SCREEN_ON -> startPolling()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(applicationContext)
        overlayManager = OverlayManager(applicationContext)
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        createNotificationChannel() // Ensure channel exists early

        // Observe settings updates
        serviceScope.launch {
            settingsManager.selectedApps.collect { selectedApps = it }
        }
        serviceScope.launch {
            settingsManager.notificationPeriod.collect { mins ->
                limitDurationMillis = (mins * 60 * 1000).toLong()
            }
        }

        // Register Screen Receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startPolling()
        
        return START_STICKY
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        
        pollingJob = serviceScope.launch {
            // Polling Loop
            while (true) {
                checkUsage()
                delay(5000) // Poll every 5 seconds
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
    }

    // Logic for checking usage
    private fun checkUsage() {
        val time = System.currentTimeMillis()
        // Query last 10 seconds to ensure we catch the current foreground app
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )

        // Efficiently find the most recent app
        val recentStat = stats.maxByOrNull { it.lastTimeUsed }
        
        currentPackage = recentStat?.packageName ?: return

        // Core Tracking Logic
        if (selectedApps.contains(currentPackage)) {
            if (currentPackage == lastPackage) {
                // Continued usage
                currentUsageDuration += 5000
                if (currentUsageDuration >= limitDurationMillis) {
                    val durationMins = (currentUsageDuration / 60000).toInt()
                    launchOverlay(durationMins)
                    currentUsageDuration = 0 // Reset immediately to prevent loop spam while overlay is looking
                }
            } else {
                // Switched TO a monitoring app from another (or nothing)
                lastPackage = currentPackage
                currentUsageDuration = 0
            }
        } else {
            // Not a monitored app
            lastPackage = ""
            currentUsageDuration = 0
        }
    }

    private fun launchOverlay(minutes: Int) {
        serviceScope.launch(Dispatchers.Main) {
            overlayManager.showTimeUpOverlay(minutes) {
                // Actions when dismissed
                // Since we reset currentUsageDuration to 0 in checkUsage() already,
                // the user can continue and it starts counting from 0.
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus Monitor is running")
            .setContentText("Tracking your digital wellbeing.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Using our custom icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        unregisterReceiver(screenReceiver)
    }
}
