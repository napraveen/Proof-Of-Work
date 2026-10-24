package com.proofofwork.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.proofofwork.ProofOfWorkApp
import com.proofofwork.core.data.AppUserPreferences
import com.proofofwork.ui.screens.BlockerOverlayActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class AppBlockerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var cachedPreferences: AppUserPreferences? = null
    
    // Millisecond wall-clock tracking: zero battery drain, zero polling loops
    private var sessionStartRealtime: Long = 0L
    private var activeBlockedPackage: String? = null
    private var cutoffJob: Job? = null
    private var lastBlockedLaunchTime: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                notificationTimeout = 100
            }
            serviceInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "Error setting service info", e)
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
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        // NEVER intercept system settings, launchers, or package managers
        if (isSystemOrSafePackage(packageName)) {
            finalizeActiveSession()
            return
        }

        val prefs = cachedPreferences ?: return
        if (!prefs.isServiceEnabled) return

        if (prefs.blockedPackages.contains(packageName)) {
            handleBlockedAppLaunch(packageName, prefs)
        } else {
            // User switched to another allowed app (e.g. WhatsApp, Chrome)
            finalizeActiveSession()
        }
    }

    private fun handleBlockedAppLaunch(packageName: String, prefs: AppUserPreferences) {
        val remainingSeconds = prefs.remainingSeconds

        if (remainingSeconds > 0) {
            // Balance available: start session using timestamp delta (zero polling loop)
            startSessionIfNew(packageName, remainingSeconds)
        } else {
            // Balance is 0: Lock immediately! Never leave blocked apps open
            triggerLockOverlay(packageName)
        }
    }

    private fun startSessionIfNew(packageName: String, remainingSeconds: Int) {
        if (activeBlockedPackage != packageName || sessionStartRealtime == 0L) {
            finalizeActiveSession()
            activeBlockedPackage = packageName
            sessionStartRealtime = SystemClock.elapsedRealtime()

            // Schedule one-shot cutoff when the remaining seconds expire
            cutoffJob?.cancel()
            cutoffJob = serviceScope.launch {
                delay(remainingSeconds * 1000L)
                withContext(Dispatchers.Main) {
                    triggerLockOverlay(packageName)
                }
            }
        }
    }

    private fun triggerLockOverlay(packageName: String) {
        finalizeActiveSession()
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockedLaunchTime > 500L) {
            lastBlockedLaunchTime = now
            launchBlockerOverlay(packageName)
        }
    }

    private fun finalizeActiveSession() {
        cutoffJob?.cancel()
        cutoffJob = null

        if (sessionStartRealtime > 0L) {
            val elapsedMs = SystemClock.elapsedRealtime() - sessionStartRealtime
            val secondsSpent = (elapsedMs / 1000L).toInt()
            sessionStartRealtime = 0L
            activeBlockedPackage = null

            if (secondsSpent > 0) {
                // Update in-memory cachedPreferences immediately to eliminate race conditions
                cachedPreferences = cachedPreferences?.let { current ->
                    current.copy(usedSecondsToday = current.usedSecondsToday + secondsSpent)
                }
                serviceScope.launch {
                    try {
                        ProofOfWorkApp.instance.preferencesRepository.addUsedSeconds(secondsSpent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error recording used seconds", e)
                    }
                }
            }
        }
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

    private fun isSystemOrSafePackage(pkg: String): Boolean {
        return pkg == packageName ||
                pkg == "com.android.settings" ||
                pkg == "com.google.android.packageinstaller" ||
                pkg == "com.android.packageinstaller" ||
                pkg == "com.android.systemui" ||
                pkg == "android" ||
                pkg.contains("launcher", ignoreCase = true)
    }

    override fun onInterrupt() {
        finalizeActiveSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        finalizeActiveSession()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AppBlockerService"
    }
}
