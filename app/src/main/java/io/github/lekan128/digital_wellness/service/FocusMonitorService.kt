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
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.lekan128.digital_wellness.MainActivity
import io.github.lekan128.digital_wellness.R
import io.github.lekan128.digital_wellness.data.SettingsManager
import io.github.lekan128.digital_wellness.data.TrackingStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FocusMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // Polling optimization: variables declared outside the loop
//    private var currentPackage: String = ""
    private var lastDetectedTrackablePackage: String = ""
    private var currentUsageDuration: Long = 0L
    private var limitDurationMillis: Long = 20 * 60 * 1000L // Default 20 mins
    private var selectedApps: Set<String> = emptySet()
    
    // Settings
    private var isSoundEnabled: Boolean = true
    private var isVibrationEnabled: Boolean = true
    
    // Sticky Foreground Package
//    private var detectedForegroundPackage: String? = null
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var stateStore: TrackingStateStore

    private val CHANNEL_ID = "FocusMonitorChannel"
    private val ALERT_CHANNEL_ID = "FocusAlertChannel_Dynamic" // New ID for dynamic settings
    private val NOTIFICATION_ID = 1234
    private val ALERT_NOTIFICATION_ID = 9999
    private val ACTION_DISMISS = "io.github.lekan128.digital_wellness.ACTION_DISMISS"
    
    companion object {
        const val ACTION_START_MONITORING = "io.github.lekan128.digital_wellness.ACTION_START_MONITORING"
    }

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
        stateStore = TrackingStateStore(applicationContext)
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        createMonitoringNotificationChannel()
        createAlertChannel() // Create the high priority channel

        // Observe settings updates
        serviceScope.launch {
            settingsManager.selectedApps.collect { selectedApps = it }
        }
        serviceScope.launch {
            settingsManager.notificationPeriod.collect { mins ->
                limitDurationMillis = (mins * 60 * 1000).toLong()
            }
        }
        serviceScope.launch {
            settingsManager.soundEnabled.collect { isSoundEnabled = it }
        }
        serviceScope.launch {
            settingsManager.vibrationEnabled.collect { isVibrationEnabled = it }
        }

        // Register Screen Receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(ALERT_NOTIFICATION_ID)
            // Also reset usage to allow user to continue if they wish, or it's already reset in checkUsage
             currentUsageDuration = 0
             // Save cleared state
             stateStore.saveState(lastDetectedTrackablePackage, 0)
            return START_STICKY
        }

        createMonitoringNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Restore State if valid
        val restored = stateStore.restoreState()
        lastDetectedTrackablePackage = restored.lastDetectedPackageName
        
        val isManualStart = intent?.action == ACTION_START_MONITORING
        
        // Condition 1: Was Monitoring? (Skip check if manual start)
//        if (!isManualStart
////            && !restored.isMonitoring
//            ) {
//            stopSelf()
//            return START_NOT_STICKY
//        }

        // Condition 2: Is Session Valid?
        if (isManualStart) {
            // Fresh start
            lastDetectedTrackablePackage = ""
            currentUsageDuration = 0
            stateStore.saveState(lastDetectedTrackablePackage, 0)
        } else {
            val timeGap = System.currentTimeMillis() - restored.lastUpdateTimestamp
            // Need to check current app to validate session
            val currentApp = determineForegroundApp()

            if (timeGap < 4 * 60 * 1000 && currentApp == restored.lastDetectedPackageName) {
                // If it's been less than 4 minutes since the last check and the user is still on the same app
                // User is still in the same session
                lastDetectedTrackablePackage = restored.lastDetectedPackageName //maybe just leave these 2
                currentUsageDuration = restored.currentConsecutiveTime
            } else {
                // Reset if session invalid or too old
                lastDetectedTrackablePackage = ""
                currentUsageDuration = 0
            }
        }

        startPolling()
        
        return START_STICKY
    }
    
    // ... polling methods ...

    private fun startPolling() {
        Log.i("checkUsage", "startPolling ")
        if (pollingJob?.isActive == true) return
        
        pollingJob = serviceScope.launch {
            while (true) {
                checkUsage()
                // Save state after check
                // We assume isMonitoring is true since service is running
                stateStore.saveState(lastDetectedTrackablePackage, currentUsageDuration)
                delay(5000) 
            }
        }
    }

    private fun stopPolling() {
        Log.i("checkUsage", "stopPolling: screen off ")
        pollingJob?.cancel()
    }

    private fun determineForegroundApp(): String? {
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 5 * 60 * 1000, time)
        
        var detectedPackage: String? = null
        val event = android.app.usage.UsageEvents.Event() 

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
               detectedPackage = event.packageName
            } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED) {
                if (event.packageName == detectedPackage) {
                    detectedPackage = null
                }
            }
        }
        return detectedPackage
    }

    private fun checkUsage() {
        val newEventPackage = determineForegroundApp() ?: lastDetectedTrackablePackage


//        currentPackage = newEventPackage ?: lastDetectedTrackablePackage

        if (newEventPackage.isEmpty()) return

        // If it's not an app we care about, reset and exit
        if (!selectedApps.contains(newEventPackage)){
            lastDetectedTrackablePackage = ""
            currentUsageDuration = 0
            return
        }

        // If the app changed, reset the timer for the new app
        if (newEventPackage != lastDetectedTrackablePackage){
            lastDetectedTrackablePackage = newEventPackage
            currentUsageDuration = 0
            return
        }

        Log.i("checkUsage",
            "current: $newEventPackage, last: $lastDetectedTrackablePackage time: " + ((currentUsageDuration + 5000)/(1000*60))
        )

                currentUsageDuration += 5000
                if (currentUsageDuration >= limitDurationMillis) {
                    val durationMins = (currentUsageDuration / 60000).toInt()
                    // Replacing overlay launch with Notification
                    sendTimeUpNotification(durationMins)
                    currentUsageDuration = 0 
                }

    }

    // New Function
    private fun sendTimeUpNotification(minutes: Int) {
        val dismissIntent = Intent(this, FocusMonitorService::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getService(
            this, 0, dismissIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Digital Wellness Alert")
            .setContentText("Time's Up! You've successfully used $minutes minutes on this app.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher_foreground, "Dismiss", dismissPendingIntent) // Reuse icon for action
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, notification)
        
        // Manual Sound & Vibration Logic
        if (isSoundEnabled) {
            try {
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(applicationContext, notificationUri)
                r.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (isVibrationEnabled) {
             val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 1000), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 1000), -1)
            }
        }
    }

    private fun createMonitoringNotificationChannel() {
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

    private fun createAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Focus Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alerts for focus usage limits"
                enableVibration(false) // Handle manually
                setSound(null, null) // Handle manually
            }
            
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
            .setSmallIcon(R.drawable.ic_launcher_foreground) 
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        unregisterReceiver(screenReceiver)
    }
}
