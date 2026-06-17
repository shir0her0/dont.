package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private var timeTransitionedAwayFromActivePackage: Long = 0L
    private var sessionStartedTime: Long = 0L

    private val launcherPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Broadcast receiver to detect screen turn-off events and lock session.
    private val screenEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                if (activeSessionPackage != null && timeTransitionedAwayFromActivePackage == 0L) {
                    timeTransitionedAwayFromActivePackage = System.currentTimeMillis()
                    Log.d("TakeASecService", "Screen turned off. Starting away timer for $activeSessionPackage.")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        updateLauncherPackages()
        try {
            registerReceiver(screenEventsReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        } catch (e: Exception) {
            Log.e("TakeASecService", "Error registering screen broadcast receiver", e)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenEventsReceiver)
        } catch (e: Exception) {
            Log.e("TakeASecService", "Error unregistering receiver", e)
        }
        super.onDestroy()
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

    /**
     * Determines whether the focused package represents a completely transient helper overlay
     * (e.g. keyboard, dialer, autofill popup, volume panel) that should NOT be considered "leaving the app".
     */
    private fun isTransientHelper(pkg: String): Boolean {
        if (pkg == this.packageName) return true
        
        val lower = pkg.lowercase()
        // Ignore keyboards/input methods
        if (lower.contains("inputmethod") || lower.contains("keyboard") || lower.contains("ime") || lower.contains("honeyboard")) return true
        // Ignore phone dialer, calls, system overlays related to calling
        if (lower.contains("dialer") || lower.contains("telephony") || lower.contains("incallui") || lower.contains("phone") || lower.contains("call")) return true
        // Ignore Android play services autofill, text selection, permission, and credential overlays
        if (pkg == "com.google.android.gms" || pkg == "com.android.credentialmanager") return true
        // Ignore transient system packages (volume panel, screenshot overlays, power menu inside SystemUI)
        if (pkg == "com.android.systemui" || pkg == "android") return true
        
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            val app = application as? TakeASecApplication ?: return
            val repository = app.repository

            // 1. If it's a completely transient helper overlay, do absolutely nothing. We maintain the current session untouched.
            if (isTransientHelper(packageName)) {
                Log.d("TakeASecService", "Transient helper focused: $packageName. Maintaining active session state.")
                return
            }

            // 2. If focus shifted directly to another monitored app, invalidate previous session immediately.
            if (activeSessionPackage != null && activeSessionPackage != packageName && repository.isPackageMonitored(packageName)) {
                Log.d("TakeASecService", "Session for $activeSessionPackage invalidated because user switched directly to another monitored app: $packageName")
                activeSessionPackage = null
                timeTransitionedAwayFromActivePackage = 0L
            }

            // 3. If we are currently inside or returning to the active session package
            if (packageName == activeSessionPackage) {
                // Check if re-intervention timer has run out
                val reInterventionMin = repository.getReInterventionMinutes(packageName)
                if (reInterventionMin > 0) {
                    val spentMs = System.currentTimeMillis() - sessionStartedTime
                    val spentMinutes = spentMs / (1000L * 60)
                    if (spentMinutes >= reInterventionMin) {
                        Log.d("TakeASecService", "Re-intervention triggered! Focused check inside $packageName shows elapsed time of $spentMinutes minutes exceeds allowed limit $reInterventionMin.")
                        // Invalidate active session & clear bypass duration to trigger an immediate new intervention
                        activeSessionPackage = null
                        sessionStartedTime = 0L
                        timeTransitionedAwayFromActivePackage = 0L
                        repository.clearBypass(packageName)

                        if (repository.shouldIntercept(packageName)) {
                            Log.d("TakeASecService", "Intercepting app via Re-intervention: $packageName")
                            val intent = Intent(this@TakeASecAccessibilityService, InterventionActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or 
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("TARGET_PACKAGE_NAME", packageName)
                            }
                            startActivity(intent)
                        }
                        return
                    }
                }

                // Check if we were away from the active app package for too long
                if (timeTransitionedAwayFromActivePackage > 0L) {
                    val awayTime = System.currentTimeMillis() - timeTransitionedAwayFromActivePackage
                    if (awayTime > 25000L) { // 25 seconds away timeout
                        Log.d("TakeASecService", "Session for $activeSessionPackage invalidated due to being away for $awayTime ms")
                        activeSessionPackage = null
                        timeTransitionedAwayFromActivePackage = 0L
                    } else {
                        // Returned within away timeout, keep session alive
                        Log.d("TakeASecService", "Returned to $activeSessionPackage within away timeout ($awayTime ms)")
                        timeTransitionedAwayFromActivePackage = 0L
                        return
                    }
                } else {
                    // Continuing inside active session without leaving
                    return
                }
            }

            // 4. If focused screen is a non-monitored package (which is not a transient helper, i.e. laundry, settings, or other normal app)
            // we start the "away" tracker to monitor how long the user stays away from the active package.
            if (!repository.isPackageMonitored(packageName)) {
                if (activeSessionPackage != null && timeTransitionedAwayFromActivePackage == 0L) {
                    Log.d("TakeASecService", "Exited active package $activeSessionPackage to $packageName. Starting away timer.")
                    timeTransitionedAwayFromActivePackage = System.currentTimeMillis()
                }
                return
            }

            // 5. Package is monitored but currently has no active session or session was invalidated. Check if bypass active
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
                // Currently bypassed (user just completed breathing). Start a new active session!
                activeSessionPackage = packageName
                sessionStartedTime = System.currentTimeMillis()
                timeTransitionedAwayFromActivePackage = 0L
                Log.d("TakeASecService", "New active session started for bypassed app: $packageName at $sessionStartedTime")
            }
        }
    }

    override fun onInterrupt() {
        Log.d("TakeASecService", "Accessibility Service Interrupted")
    }
}
