package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppConfig
import com.example.data.Settings
import com.example.data.TakeASecRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class AppStats(
    val packageName: String,
    val appName: String,
    val totalInterventions: Int,
    val minutesSaved: Int,
    val reInterventionMinutes: Int = 0
)

data class DashboardUiState(
    val interventionsToday: Int = 0,
    val interventionsThisWeek: Int = 0,
    val estimatedMinutesSaved: Int = 0,
    val mostInterruptedAppName: String = "None yet",
    val monitoredAppStats: List<AppStats> = emptyList(),
    val settings: Settings = Settings()
)

class MainViewModel(private val repository: TakeASecRepository) : ViewModel() {

    // Installed application list on device (loaded on request)
    private val _installedApps = MutableStateFlow<List<AppConfig>>(emptyList())
    val installedApps: StateFlow<List<AppConfig>> = _installedApps.asStateFlow()

    private val _isScanningApps = MutableStateFlow(false)
    val isScanningApps: StateFlow<Boolean> = _isScanningApps.asStateFlow()

    // Dashboard reactive flow
    val uiState: StateFlow<DashboardUiState> = combine(
        repository.monitoredConfigs,
        repository.allLogs,
        repository.settings
    ) { configs, logs, currentSettings ->
        // Start of Today calculation
        val todayCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartMs = todayCalendar.timeInMillis
        val weekStartMs = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

        // Today metrics
        val todayInterventions = logs.count { it.timestamp >= todayStartMs }
        val weekInterventions = logs.count { it.timestamp >= weekStartMs }

        // Generate stats array per app configured in DB
        val appStatsList = configs.map { config ->
            val appLogs = logs.filter { it.packageName == config.packageName }
            val totalInterventionsCount = appLogs.size
            val appCancelledCount = appLogs.count { it.wasCancelled }
            
            // Calculate dynamic session length from usage statistics
            val appSessionLengthMinutes = repository.getAverageSessionDurationMinutes(config.packageName)
            val appMinutesSaved = appCancelledCount * appSessionLengthMinutes

            AppStats(
                packageName = config.packageName,
                appName = config.appName,
                totalInterventions = totalInterventionsCount,
                minutesSaved = appMinutesSaved,
                reInterventionMinutes = config.reInterventionMinutes
            )
        }.sortedByDescending { it.totalInterventions }

        // Total saved minutes based on dynamic session calculations for each app
        val minutesSaved = appStatsList.sumOf { it.minutesSaved }

        // Find most interrupted app
        val countsMap = logs.groupingBy { it.packageName }.eachCount()
        val mostInterruptedPkg = countsMap.maxByOrNull { it.value }?.key
        val mostInterruptedLabel = if (mostInterruptedPkg != null) {
            configs.find { it.packageName == mostInterruptedPkg }?.appName
                ?: mostInterruptedPkg.split(".").lastOrNull() ?: mostInterruptedPkg
        } else {
            "None yet"
        }

        DashboardUiState(
            interventionsToday = todayInterventions,
            interventionsThisWeek = weekInterventions,
            estimatedMinutesSaved = minutesSaved,
            mostInterruptedAppName = mostInterruptedLabel,
            monitoredAppStats = appStatsList,
            settings = currentSettings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    // Trigger full background scan of launcher apps on device
    fun scanInstalledLauncherApps(context: Context) {
        viewModelScope.launch {
            _isScanningApps.value = true
            try {
                val fullList = repository.getInstalledLauncherApps(context)
                _installedApps.value = fullList
            } catch (e: Exception) {
                // Return empty or fallback
                _installedApps.value = emptyList()
            } finally {
                _isScanningApps.value = false
            }
        }
    }

    // Monitoring status controllers
    fun toggleAppMonitoring(packageName: String, appName: String, isMonitored: Boolean) {
        viewModelScope.launch {
            repository.registerApp(AppConfig(packageName, appName, isMonitored))
        }
    }

    fun removeMonitoredApp(packageName: String) {
        viewModelScope.launch {
            repository.setMonitoringStatus(packageName, isMonitored = false)
        }
    }

    fun updateReInterventionSetting(packageName: String, minutes: Int) {
        viewModelScope.launch {
            repository.setReInterventionSetting(packageName, minutes)
        }
    }

    // Settings modifications
    fun updateMindfulnessText(text: String) {
        viewModelScope.launch {
            val current = uiState.value.settings
            repository.saveSettings(current.copy(mindfulnessText = text))
        }
    }

    fun updateDelayDuration(seconds: Int) {
        viewModelScope.launch {
            val current = uiState.value.settings
            repository.saveSettings(current.copy(delayDurationSeconds = seconds))
        }
    }

    fun updateAverageSessionLength(minutes: Int) {
        viewModelScope.launch {
            val current = uiState.value.settings
            repository.saveSettings(current.copy(averageSessionLengthMinutes = minutes))
        }
    }

    // Clear history helper (nice feature for users restarting)
    // Wait, let's keep database neat. Removing records is handled easily.
}

class MainViewModelFactory(private val repository: TakeASecRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
