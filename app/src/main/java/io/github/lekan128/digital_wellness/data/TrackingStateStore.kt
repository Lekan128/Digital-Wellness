package io.github.lekan128.digital_wellness.data

import android.content.Context
import android.content.SharedPreferences

data class TrackingState(
    val isMonitoring: Boolean,
    val lastPackageName: String,
    val currentConsecutiveTime: Long,
    val lastUpdateTimestamp: Long
)

class TrackingStateStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_MONITORING = "is_monitoring"
        private const val KEY_LAST_PACKAGE_NAME = "last_package_name"
        private const val KEY_CURRENT_CONSECUTIVE_TIME = "current_consecutive_time"
        private const val KEY_LAST_UPDATE_TIMESTAMP = "last_update_timestamp"
    }

    fun saveState(isMonitoring: Boolean, lastPackageName: String, currentConsecutiveTime: Long) {
        prefs.edit().apply {
            putBoolean(KEY_IS_MONITORING, isMonitoring)
            putString(KEY_LAST_PACKAGE_NAME, lastPackageName)
            putLong(KEY_CURRENT_CONSECUTIVE_TIME, currentConsecutiveTime)
            putLong(KEY_LAST_UPDATE_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
    }

    fun restoreState(): TrackingState {
        return TrackingState(
            isMonitoring = prefs.getBoolean(KEY_IS_MONITORING, false), // Default to false or implied running?
            // Actually, if we are restoring, we probably want to default to empty/0 if nothing exists.
            lastPackageName = prefs.getString(KEY_LAST_PACKAGE_NAME, "") ?: "",
            currentConsecutiveTime = prefs.getLong(KEY_CURRENT_CONSECUTIVE_TIME, 0L),
            lastUpdateTimestamp = prefs.getLong(KEY_LAST_UPDATE_TIMESTAMP, 0L)
        )
    }
    
    fun clearState() {
        prefs.edit().clear().apply()
    }
}
