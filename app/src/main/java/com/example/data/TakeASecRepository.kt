package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class TakeASecRepository(private val context: Context, private val dao: TakeASecDao) {

    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val cachedMonitoredPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Transient memory of bypassed packages to prevent loops after "Open App/Continue"
    // PackageName -> Expiration Timestamp (System.currentTimeMillis())
    private val bypassedApps = ConcurrentHashMap<String, Long>()

    // Flows for UI binding
    val allConfigs: Flow<List<AppConfig>> = dao.getAllConfigs()
    val monitoredConfigs: Flow<List<AppConfig>> = dao.getMonitoredConfigs()
    val allLogs: Flow<List<InterventionLog>> = dao.getAllLogs()
    
    // Automatically retrieve settings or supply defaults if not written yet
    val settings: Flow<Settings> = dao.getSettings().map { it ?: Settings() }

    init {
        repositoryScope.launch {
            monitoredConfigs.collect { configs ->
                cachedMonitoredPackages.clear()
                configs.forEach { cachedMonitoredPackages.add(it.packageName) }
                Log.d("TakeASecRepo", "Cached monitored packages updated: $cachedMonitoredPackages")
            }
        }
    }

    // Check if package should be intercepted (is monitored AND is not currently bypassed)
    fun shouldIntercept(packageName: String): Boolean {
        // First check transient bypass
        val expiration = bypassedApps[packageName]
        if (expiration != null) {
            if (System.currentTimeMillis() < expiration) {
                // Currently bypassed, do not intercept
                return false
            } else {
                // Bypass expired, clean up
                bypassedApps.remove(packageName)
            }
        }

        // Fast O(1) in-memory check instead of fetching from disk/Db
        return cachedMonitoredPackages.contains(packageName)
    }

    // Public in-memory check to see if package is currently monitored
    fun isPackageMonitored(packageName: String): Boolean {
        return cachedMonitoredPackages.contains(packageName)
    }

    // Set bypass for a package to allow entry after completing the intervention countdown
    fun bypassAppForDuration(packageName: String, durationSeconds: Int = 15) {
        val expirationTime = System.currentTimeMillis() + (durationSeconds * 1000)
        bypassedApps[packageName] = expirationTime
        Log.d("TakeASecRepo", "Bypassed $packageName for $durationSeconds seconds")
    }

    // Clear bypass
    fun clearBypass(packageName: String) {
        bypassedApps.remove(packageName)
    }

    // Insert or update settings
    suspend fun saveSettings(newSettings: Settings) = withContext(Dispatchers.IO) {
        dao.insertOrUpdateSettings(newSettings)
    }

    // Insert individual intervention log
    suspend fun logIntervention(packageName: String, wasCancelled: Boolean) = withContext(Dispatchers.IO) {
        dao.insertLog(InterventionLog(packageName = packageName, wasCancelled = wasCancelled))
    }

    // Toggle monitoring status
    suspend fun setMonitoringStatus(packageName: String, isMonitored: Boolean) = withContext(Dispatchers.IO) {
        dao.updateMonitoringStatus(packageName, isMonitored)
    }

    // Insert app configuration
    suspend fun registerApp(config: AppConfig) = withContext(Dispatchers.IO) {
        dao.insertConfig(config)
    }

    // Add list of configs
    suspend fun registerApps(configs: List<AppConfig>) = withContext(Dispatchers.IO) {
        dao.insertConfigs(configs)
    }

    // Check settings and default common apps, prepopulating if empty
    suspend fun initializeDatabaseIfEmpty() = withContext(Dispatchers.IO) {
        // Pre-write default settings if none exists
        val currentSettings = dao.getSettingsDirect()
        if (currentSettings == null) {
            dao.insertOrUpdateSettings(Settings())
        }

        // Prepopulate with a baseline list of popular heavy-use apps to show as options
        val monitoredNames = dao.getMonitoredPackageNames()
        if (monitoredNames.isEmpty()) {
            val starterApps = listOf(
                AppConfig("com.instagram.android", "Instagram", isMonitored = false),
                AppConfig("com.zhiliaoapp.musically", "TikTok", isMonitored = false),
                AppConfig("com.facebook.katana", "Facebook", isMonitored = false),
                AppConfig("com.twitter.android", "X / Twitter", isMonitored = false),
                AppConfig("com.reddit.frontpage", "Reddit", isMonitored = false),
                AppConfig("com.google.android.youtube", "YouTube", isMonitored = false),
                AppConfig("com.snapchat.android", "Snapchat", isMonitored = false)
            )
            dao.insertConfigs(starterApps)
        }
    }

    // Local function to help scan the device's launcher package list
    suspend fun getInstalledLauncherApps(context: Context): List<AppConfig> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = pm.queryIntentActivities(mainIntent, 0)
        
        val appList = mutableListOf<AppConfig>()
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in resolveInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            // Skip our own app
            if (packageName == context.packageName) continue

            if (seenPackages.add(packageName)) {
                val label = resolveInfo.loadLabel(pm).toString()
                appList.add(AppConfig(packageName = packageName, appName = label, isMonitored = false))
            }
        }

        appList.sortedBy { it.appName }
    }

    // Query usage statistics utilizing UsageStatsManager API to dynamically estimate user average session length
    fun getAverageSessionDurationMinutes(packageName: String): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            ?: return 5 // Fallback to 5 mins

        val endTime = System.currentTimeMillis()
        val startTime = endTime - 14 * 24 * 60 * 60 * 1000L // Query the last 14 days of history to get a resilient average

        val events = try {
            usageStatsManager.queryEvents(startTime, endTime)
        } catch (e: SecurityException) {
            null
        } ?: return 5 // Fallback to 5 mins if permission is not granted

        var totalSessionTimeMs = 0L
        var sessionCount = 0
        var lastForegroundTime = 0L

        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == packageName) {
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForegroundTime = event.timeStamp
                } else if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    if (lastForegroundTime > 0L) {
                        val duration = event.timeStamp - lastForegroundTime
                        if (duration in 1..7200000L) { // Filter out unrealistic sessions (> 2 hours)
                            totalSessionTimeMs += duration
                            sessionCount++
                        }
                        lastForegroundTime = 0L
                    }
                }
            }
        }

        if (sessionCount > 0) {
            val averageMillis = totalSessionTimeMs / sessionCount
            val averageMinutes = (averageMillis / (1000L * 60L)).toInt()
            return if (averageMinutes > 0) averageMinutes else 1 // Minimum 1 minute
        }

        return 5 // Default fallback if no usage events found
    }
}
