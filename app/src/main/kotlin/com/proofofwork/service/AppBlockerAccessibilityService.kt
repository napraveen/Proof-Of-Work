package com.proofofwork.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.proofofwork.ProofOfWorkApp
import com.proofofwork.core.data.AppUserPreferences
import com.proofofwork.core.model.NetworkResult
import com.proofofwork.core.network.GitHubService
import com.proofofwork.core.network.LeetCodeService
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

    // Just-in-time verification throttle (avoids rapid re-checking)
    private var lastAutoCheckTime: Long = 0L
    private var isCheckingJustInTime: Boolean = false

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
            // Balance is 0: Check if we can do a Just-In-Time Auto-Verification
            val canCheckGitHub = !prefs.isGithubPushedToday && prefs.githubUsername.isNotBlank()
            val canCheckLeetCode = !prefs.isLeetcodeSolvedToday && prefs.leetcodeUsername.isNotBlank()

            val now = SystemClock.elapsedRealtime()
            val hasCooldownElapsed = (now - lastAutoCheckTime) > (3 * 60 * 1000L) // 3-minute throttle

            if ((canCheckGitHub || canCheckLeetCode) && hasCooldownElapsed && !isCheckingJustInTime) {
                // Perform Just-In-Time verification in the background
                runJustInTimeVerification(packageName, prefs)
            } else {
                // Either already verified both today, or in throttle cooldown: show lock screen
                triggerLockOverlay(packageName)
            }
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
                    launchBlockerOverlay(packageName)
                }
            }
        }
    }

    private fun runJustInTimeVerification(packageName: String, prefs: AppUserPreferences) {
        isCheckingJustInTime = true
        lastAutoCheckTime = SystemClock.elapsedRealtime()

        serviceScope.launch {
            try {
                val repo = ProofOfWorkApp.instance.preferencesRepository
                var foundWork = false

                // 1. Quick check GitHub
                if (!prefs.isGithubPushedToday && prefs.githubUsername.isNotBlank()) {
                    val result = GitHubService.verifyTodayPush(prefs.githubUsername)
                    if (result is NetworkResult.Success && result.data.isPushedToday) {
                        repo.updateGithubVerified(true)
                        foundWork = true
                    }
                }

                // 2. Quick check LeetCode
                if (!prefs.isLeetcodeSolvedToday && prefs.leetcodeUsername.isNotBlank()) {
                    val result = LeetCodeService.verifyTodaySolve(prefs.leetcodeUsername)
                    if (result is NetworkResult.Success && result.data.isSolvedToday) {
                        repo.updateLeetcodeVerified(true)
                        foundWork = true
                    }
                }

                if (foundWork) {
                    // Proof of work verified! Fetch refreshed preferences and let user in seamlessly
                    val updatedPrefs = repo.userPreferencesFlow.first()
                    cachedPreferences = updatedPrefs
                    val newRemaining = updatedPrefs.remainingSeconds

                    if (newRemaining > 0) {
                        withContext(Dispatchers.Main) {
                            startSessionIfNew(packageName, newRemaining)
                        }
                        return@launch
                    }
                }

                // If no proof-of-work found, lock now
                withContext(Dispatchers.Main) {
                    triggerLockOverlay(packageName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Just-in-time check failed", e)
                withContext(Dispatchers.Main) {
                    triggerLockOverlay(packageName)
                }
            } finally {
                isCheckingJustInTime = false
            }
        }
    }

    private fun triggerLockOverlay(packageName: String) {
        finalizeActiveSession()
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockedLaunchTime > 1200L) {
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
