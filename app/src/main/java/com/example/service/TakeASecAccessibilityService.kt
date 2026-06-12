package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.TakeASecApplication
import com.example.ui.InterventionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TakeASecAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Track the package that currently has an active, bypassed usage session
    private var activeSessionPackage: String? = null
    private var activeSessionLastFocusedTime: Long = 0L

    // Known system/utility packages to ignore for session invalidation
    private val knownSystemPackages = setOf(
        "com.android.systemui",
        "com.android.settings",
        "android"
    )
    private val launcherPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        updateLauncherPackages()
    }

    private fun updateLauncherPackages() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val pm = packageManager
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            for (info in resolveInfos) {
                info.activityInfo?.packageName?.let {
                    launcherPackages.add(it)
                }
            }
            Log.d("TakeASecService", "Detected launchers: $launcherPackages")
        } catch (e: Exception) {
            Log.e("TakeASecService", "Error querying launchers", e)
        }
    }

    private fun isSystemOrLauncher(pkg: String): Boolean {
        if (pkg == this.packageName) return true
        if (knownSystemPackages.contains(pkg)) return true
        if (launcherPackages.contains(pkg)) return true
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            val app = application as? TakeASecApplication ?: return
            val repository = app.repository

            if (isSystemOrLauncher(packageName)) {
                // User is in system space, settings, our app, or the launcher.
                // Keep the current session alive but let time-spent count towards inactivity.
                // If they stay away in system/launcher for more than 20 seconds, clear the active session.
                if (activeSessionPackage != null) {
                    val timeSinceLastFocus = System.currentTimeMillis() - activeSessionLastFocusedTime
                    if (timeSinceLastFocus > 20000) {
                        Log.d("TakeASecService", "Session for $activeSessionPackage invalidated due to 20s away")
                        activeSessionPackage = null
                    }
                }
                return
            }

            // At this point, the user is in a normal non-system/non-launcher user app.
            
            // First, if they were in an active session, check if they spent too long away (e.g. phone locked for an hour)
            if (activeSessionPackage != null) {
                val timeSinceLastFocus = System.currentTimeMillis() - activeSessionLastFocusedTime
                if (timeSinceLastFocus > 20000) {
                    Log.d("TakeASecService", "Session for $activeSessionPackage invalidated due to inactivity ($timeSinceLastFocus ms)")
                    activeSessionPackage = null
                }
            }

            // If we transitioned to a different user app, invalidate the previous session immediately
            if (activeSessionPackage != null && activeSessionPackage != packageName) {
                Log.d("TakeASecService", "Session for $activeSessionPackage invalidated because user switched to: $packageName")
                activeSessionPackage = null
            }

            // If returning to or continuing the active session
            if (packageName == activeSessionPackage) {
                activeSessionLastFocusedTime = System.currentTimeMillis()
                Log.d("TakeASecService", "Continuing active session for $packageName, updating timestamp")
                return
            }

            // Check if the newly focused user app is monitored
            if (repository.isPackageMonitored(packageName)) {
                if (repository.shouldIntercept(packageName)) {
                    Log.d("TakeASecService", "Intercepting app: $packageName")
                    
                    val intent = Intent(this@TakeASecAccessibilityService, InterventionActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or 
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("TARGET_PACKAGE_NAME", packageName)
                    }
                    startActivity(intent)
                } else {
                    // It is monitored but currently bypassed (user just completed breathing)
                    // Start a new active session!
                    activeSessionPackage = packageName
                    activeSessionLastFocusedTime = System.currentTimeMillis()
                    Log.d("TakeASecService", "New active session started for bypassed app: $packageName")
                }
            } else {
                // Focus shifted to a non-monitored user-centric app. Ensure no active session remains.
                activeSessionPackage = null
            }
        }
    }

    override fun onInterrupt() {
        Log.d("TakeASecService", "Accessibility Service Interrupted")
    }
}
