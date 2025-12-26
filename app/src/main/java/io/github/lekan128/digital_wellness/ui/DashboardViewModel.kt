package io.github.lekan128.digital_wellness.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lekan128.digital_wellness.data.AppInfo
import io.github.lekan128.digital_wellness.data.AppRepository
import io.github.lekan128.digital_wellness.data.SettingsManager
import io.github.lekan128.digital_wellness.service.FocusMonitorService
import io.github.lekan128.digital_wellness.util.PermissionUtils
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.lekan128.digital_wellness.worker.ServiceCheckWorker
import java.util.concurrent.TimeUnit
import io.github.lekan128.digital_wellness.data.TrackingStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val settingsManager = SettingsManager(application)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    
    // New state for missing permissions
    private val _missingPermissions = MutableStateFlow<List<PermissionType>>(emptyList())
    val missingPermissions: StateFlow<List<PermissionType>> = _missingPermissions

    val selectedApps = settingsManager.selectedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val notificationPeriod = settingsManager.notificationPeriod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20f)
        
    val isMonitoring = settingsManager.isMonitoringEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Sorted apps: Selected first, then alphabetical
    val sortedApps: StateFlow<List<AppInfo>> = combine(_apps, selectedApps) { all, selected ->
        val (selectedList, unselectedList) = all.partition { selected.contains(it.packageName) }
        selectedList.sortedBy { it.label } + unselectedList.sortedBy { it.label }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadApps()
    }
    
    fun checkPermissions() {
        // ... permission checks ...
        // Logic to sync service state with preference
        // If monitoring is enabled but service not running (how to detect?), we might want to restart.
        // For now, rely on toggle.
        
        val context = getApplication<Application>()
        val missing = mutableListOf<PermissionType>()
        
        if (!PermissionUtils.hasUsageStatsPermission(context)) {
            missing.add(PermissionType.USAGE_STATS)
        }
        if (!PermissionUtils.hasOverlayPermission(context)) {
            missing.add(PermissionType.OVERLAY)
        }
        if (!PermissionUtils.isIgnoringBatteryOptimizations(context)) {
            missing.add(PermissionType.BATTERY_OPTIMIZATION)
        }
        if (!PermissionUtils.hasNotificationPermission(context)) {
            missing.add(PermissionType.NOTIFICATIONS)
        }
        
        _missingPermissions.value = missing
        
        // Auto-start if permissions granted AND was previously monitoring?
        // Or just let user start. Let's keep manual start for clarity unless we really want auto-restore.
    }
    
    fun toggleService() {
        val context = getApplication<Application>()
        val currentStatus = isMonitoring.value
        if (currentStatus) {
            // Stop
            context.stopService(Intent(context, FocusMonitorService::class.java))
            viewModelScope.launch { settingsManager.setMonitoringEnabled(false) }
            
            // Clear tracking state
            TrackingStateStore(context).clearState()
            
            // Cancel Safety Net
            WorkManager.getInstance(context).cancelUniqueWork("FocusSafetyNet")
        } else {
            // Start
            // Synchronously set monitoring true so service doesn't kill itself
            TrackingStateStore(context).saveState(true, "", 0)
            
            val intent = android.content.Intent(context, FocusMonitorService::class.java).apply {
                action = FocusMonitorService.ACTION_START_MONITORING
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            viewModelScope.launch { settingsManager.setMonitoringEnabled(true) }
            
            // Schedule Safety Net (Every 15 mins)
            val request = PeriodicWorkRequestBuilder<ServiceCheckWorker>(15, TimeUnit.MINUTES)
                .build()
                
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "FocusSafetyNet",
                ExistingPeriodicWorkPolicy.KEEP, 
                request
            )
        }
    }
    
    private fun startFocusService(context: Application) {
        val intent = Intent(context, FocusMonitorService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadApps() {
        viewModelScope.launch {
            val installedApps = repository.getInstalledApps()
            _apps.value = installedApps
        }
    }
    
    fun performAutoSelection() {
        viewModelScope.launch {
            if (selectedApps.value.isNotEmpty()) return@launch
            
            val socialPackages = setOf(
                "com.instagram.android",
                "com.zhiliaoapp.musically",
                "com.twitter.android",
                "com.facebook.katana",
                "com.google.android.youtube",
                "com.snapchat.android"
            )
            
            val toSelect = _apps.value
                .filter { socialPackages.contains(it.packageName) }
                .map { it.packageName }
                .toSet()
            
            if (toSelect.isNotEmpty()) {
                settingsManager.saveSelectedApps(toSelect)
            }
        }
    }
    
    fun toggleAppSelection(packageName: String, isSelected: Boolean) {
        viewModelScope.launch {
            val currentSelected = selectedApps.value.toMutableSet()
            if (isSelected) {
                currentSelected.add(packageName)
            } else {
                currentSelected.remove(packageName)
            }
            settingsManager.saveSelectedApps(currentSelected)
        }
    }

    fun updateNotificationPeriod(minutes: Float) {
        viewModelScope.launch {
            settingsManager.saveNotificationPeriod(minutes)
        }
    }
}
