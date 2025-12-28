package io.github.lekan128.digital_wellness.worker

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.lekan128.digital_wellness.data.TrackingStateStore
import io.github.lekan128.digital_wellness.service.FocusMonitorService

class ServiceCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val context = applicationContext
//        val stateStore = TrackingStateStore(context)
//        val state = stateStore.restoreState()

        // 1. Should we be monitoring?
//        if (state.isMonitoring) {
            // 2. Is the service running?
            if (!isServiceRunning(context, FocusMonitorService::class.java)) {
                // 3. Restart Service
                val intent = Intent(context, FocusMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
//        }

        return Result.success()
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager?.getRunningServices(Int.MAX_VALUE) ?: emptyList()) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
