package io.github.lekan128.digital_wellness.data

import android.content.Context
import android.content.SharedPreferences

data class TrackingState(
//    val isMonitoring: Boolean,
    val lastDetectedPackageName: String,
    val currentConsecutiveTime: Long,
    val lastUpdateTimestamp: Long,
//    val detectedForegroundPackage: String? // Sticky package
)

class TrackingStateStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_MONITORING = "is_monitoring"
        private const val KEY_LAST_DETECTED_PACKAGE_NAME = "last_detected_package_name"
        private const val KEY_CURRENT_CONSECUTIVE_TIME = "current_consecutive_time"
        private const val KEY_LAST_UPDATE_TIMESTAMP = "last_update_timestamp"
//        private const val KEY_DETECTED_FOREGROUND_PACKAGE = "detected_foreground_package"
    }

    fun saveState(
//        isMonitoring: Boolean,
        lastDetectedPackageName: String,
        currentConsecutiveTime: Long,
//        detectedForegroundPackage: String?
    ) {
        prefs.edit().apply {
//            putBoolean(KEY_IS_MONITORING, isMonitoring)
            putString(KEY_LAST_DETECTED_PACKAGE_NAME, lastDetectedPackageName)
            putLong(KEY_CURRENT_CONSECUTIVE_TIME, currentConsecutiveTime)
            putLong(KEY_LAST_UPDATE_TIMESTAMP, System.currentTimeMillis())
//            putString(KEY_DETECTED_FOREGROUND_PACKAGE, detectedForegroundPackage)
            apply()
        }
    }

    fun restoreState(): TrackingState {
        return TrackingState(
//            isMonitoring = prefs.getBoolean(KEY_IS_MONITORING, false),
            lastDetectedPackageName = prefs.getString(KEY_LAST_DETECTED_PACKAGE_NAME, "") ?: "",
            currentConsecutiveTime = prefs.getLong(KEY_CURRENT_CONSECUTIVE_TIME, 0L),
            lastUpdateTimestamp = prefs.getLong(KEY_LAST_UPDATE_TIMESTAMP, 0L),
//            detectedForegroundPackage = prefs.getString(KEY_DETECTED_FOREGROUND_PACKAGE, null)
        )
    }
    
    fun clearState() {
        prefs.edit().clear().apply()
    }
}
