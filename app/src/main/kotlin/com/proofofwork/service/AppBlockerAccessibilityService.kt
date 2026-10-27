package com.proofofwork.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.proofofwork.ProofOfWorkApp
import com.proofofwork.core.data.AppUserPreferences
import com.proofofwork.ui.screens.BlockerOverlayActivity
import kotlinx.coroutines.*
import java.util.Locale

class AppBlockerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var cachedPreferences: AppUserPreferences? = null

    private var activeBlockedPackage: String? = null
    private var trackingJob: Job? = null
    private var lastBlockedLaunchTime: Long = 0L
    private var isReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.d(TAG, "Screen turned off. Pausing active session.")
                finalizeActiveSession()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                notificationTimeout = 50
            }
            serviceInfo = info
            Log.d(TAG, "AppBlockerAccessibilityService connected with multi-event listening.")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting service info", e)
        }

        // Register screen on/off listener to halt tracking when device screen is turned off
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Could not register screenReceiver", e)
        }

        // Keep local memory mirror of preferences
        serviceScope.launch {
            try {
                ProofOfWorkApp.instance.preferencesRepository.userPreferencesFlow.collect { prefs ->
                    cachedPreferences = prefs
                }
            } catch (e: Exception) {
                Log.e(TAG, "Preferences collection failed", e)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // If the blocker overlay is actively displaying on screen, ignore background app events
        if (BlockerOverlayActivity.isShowing) {
            return
        }

        // Resolve active package name even when event.packageName is null (e.g. Recent Apps switcher)
        val packageName = getActivePackageName(event) ?: return

        // If ProofOfWork itself is in foreground, finalize any active tracking session
        if (packageName == applicationContext.packageName) {
            if (activeBlockedPackage != null) {
                finalizeActiveSession()
            }
            return
        }

        // 1. Ignore transient system overlays (OnePlus overlays, keyboards, volume bars, notifications)
        if (isTransientSystemPackage(packageName)) {
            return
        }

        val prefs = cachedPreferences ?: return
        if (!prefs.isServiceEnabled) return

        // 2. Check if this is a configured blocked package (e.g. Instagram)
        if (prefs.blockedPackages.contains(packageName)) {
            handleBlockedAppLaunch(packageName, prefs)
        } else {
            // User switched to another real application, home launcher, etc.
            if (activeBlockedPackage != null) {
                Log.d(TAG, "User switched from $activeBlockedPackage to $packageName. Finalizing session.")
                finalizeActiveSession()
            }
        }
    }

    private fun getActivePackageName(event: AccessibilityEvent): String? {
        val eventPkg = event.packageName?.toString()
        if (!eventPkg.isNullOrBlank() && !isTransientSystemPackage(eventPkg)) {
            return eventPkg
        }

        // When event.packageName is null or transient (e.g. TYPE_WINDOWS_CHANGED from Recent Apps),
        // inspect the active/focused root window
        try {
            val rootPkg = rootInActiveWindow?.packageName?.toString()
            if (!rootPkg.isNullOrBlank() && !isTransientSystemPackage(rootPkg)) {
                return rootPkg
            }
        } catch (_: Exception) {}

        try {
            val activeWindowPkg = windows?.firstOrNull { it.isFocused || it.isActive }?.root?.packageName?.toString()
            if (!activeWindowPkg.isNullOrBlank() && !isTransientSystemPackage(activeWindowPkg)) {
                return activeWindowPkg
            }
        } catch (_: Exception) {}

        return eventPkg
    }

    private fun handleBlockedAppLaunch(packageName: String, prefs: AppUserPreferences) {
        if (BlockerOverlayActivity.isShowing) {
            return
        }

        if (prefs.isWeekendFreedomActive) {
            // Unrestricted Weekend Freedom: track usage smoothly without locking
            startTrackingSession(packageName)
            return
        }

        val remainingSeconds = prefs.remainingSeconds
        if (remainingSeconds > 0) {
            // Balance available: run real-time active second-by-second tracking
            startTrackingSession(packageName)
        } else {
            // Balance is 0: Lock immediately!
            Log.d(TAG, "Balance is 0 for $packageName. Triggering lock overlay.")
            triggerLockOverlay(packageName)
        }
    }

    private fun startTrackingSession(packageName: String) {
        if (activeBlockedPackage == packageName && trackingJob?.isActive == true) {
            return
        }

        finalizeActiveSession()
        activeBlockedPackage = packageName
        Log.d(TAG, "Started real-time second tracking for $packageName")

        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            var lastTickMs = SystemClock.elapsedRealtime()

            while (isActive && activeBlockedPackage == packageName) {
                delay(1000L)

                val now = SystemClock.elapsedRealtime()
                val deltaMs = now - lastTickMs

                if (deltaMs >= 1000L) {
                    val secondsToDeduct = (deltaMs / 1000L).toInt()
                    lastTickMs += secondsToDeduct * 1000L

                    // 1. Immediately update memory mirror
                    cachedPreferences = cachedPreferences?.let { current ->
                        current.copy(usedSecondsToday = current.usedSecondsToday + secondsToDeduct)
                    }

                    // 2. Persist to DataStore EVERY SECOND so ProofOfWork always has the exact second count
                    try {
                        ProofOfWorkApp.instance.preferencesRepository.addUsedSeconds(secondsToDeduct)
                        Log.d(TAG, "Instagram tick: +$secondsToDeduct s (used today: ${cachedPreferences?.usedSecondsToday}s)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding used seconds", e)
                    }

                    // 3. Check if time has expired
                    val currentPrefs = cachedPreferences
                    if (currentPrefs != null && !currentPrefs.isWeekendFreedomActive && currentPrefs.remainingSeconds <= 0) {
                        Log.d(TAG, "Time expired during active Instagram session! Triggering lock overlay.")
                        withContext(Dispatchers.Main) {
                            triggerLockOverlay(packageName)
                        }
                        break
                    }
                }
            }
        }
    }

    private fun triggerLockOverlay(packageName: String) {
        finalizeActiveSession()
        if (BlockerOverlayActivity.isShowing) {
            Log.d(TAG, "BlockerOverlayActivity is already showing. Suppressing launch.")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockedLaunchTime > 1500L) {
            lastBlockedLaunchTime = now
            launchBlockerOverlay(packageName)
        }
    }

    private fun finalizeActiveSession() {
        if (activeBlockedPackage != null) {
            Log.d(TAG, "Session finalized for $activeBlockedPackage")
        }
        trackingJob?.cancel()
        trackingJob = null
        activeBlockedPackage = null
    }

    /**
     * Packages that temporarily appear above an app (OnePlus/ColorOS system UI, keyboards,
     * notifications, volume sliders) which must NOT cancel or reset the active Instagram session.
     * Note: Launchers are NOT transient—they indicate the user pressed Home or opened Recent Apps!
     */
    private fun isTransientSystemPackage(pkg: String): Boolean {
        val lower = pkg.lowercase(Locale.US)
        if (lower.contains("launcher") || lower.contains("home")) {
            return false // Recent Apps and Home Launcher are genuine navigation screens, NOT transient!
        }
        return lower == "com.android.systemui" ||
                lower == "android" ||
                lower == "com.google.android.gms" ||
                lower.startsWith("com.coloros.") ||
                lower.startsWith("com.oplus.") ||
                lower.startsWith("com.heytap.") ||
                lower.contains("smartsidebar") ||
                lower.contains("assistantscreen") ||
                lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("honeyboard") ||
                lower.contains("ime") ||
                lower.contains("latin") ||
                lower.contains("swiftkey") ||
                lower.contains("autofill") ||
                lower.contains("toast")
    }

    private fun launchBlockerOverlay(targetPackage: String) {
        try {
            val intent = Intent(this, BlockerOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("EXTRA_TARGET_PACKAGE", targetPackage)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch BlockerOverlayActivity", e)
        }
    }

    override fun onInterrupt() {
        finalizeActiveSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        finalizeActiveSession()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
            }
        }
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AppBlockerService"
    }
}
