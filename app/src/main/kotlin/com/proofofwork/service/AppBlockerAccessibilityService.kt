package com.proofofwork.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.proofofwork.ProofOfWorkApp
import com.proofofwork.core.data.AppUserPreferences
import com.proofofwork.core.data.UserPreferencesRepository
import com.proofofwork.ui.screens.BlockerOverlayActivity
import kotlinx.coroutines.*
import java.util.Locale

class AppBlockerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var cachedPreferences: AppUserPreferences? = null

    @Volatile
    private var activeBlockedPackage: String? = null

    @Volatile
    private var lastTickElapsedMs: Long = 0L

    @Volatile
    private var unflushedRemainderMs: Long = 0L

    @Volatile
    private var lastConfirmedBlockedEventMs: Long = 0L

    @Volatile
    private var lastBlockedLaunchTime: Long = 0L

    private var watchdogJob: Job? = null
    private var isReceiverRegistered = false
    private var powerManager: PowerManager? = null

    private val screenAndDateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen turned off. Flushing and pausing active session.")
                    finalizeActiveSession(flushRemainder = true)
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    ensureWatchdogRunning()
                }
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                    serviceScope.launch(Dispatchers.IO) {
                        checkAndPerformDailyReset()
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager

        // IMPORTANT: Mutate existing serviceInfo rather than creating a blank AccessibilityServiceInfo()
        // so that XML-declared CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT is preserved!
        try {
            val info = serviceInfo
            if (info != null) {
                info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED
                info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                info.flags = info.flags or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                info.notificationTimeout = 50
                serviceInfo = info
            }
            Log.i(TAG, "AppBlockerAccessibilityService connected (capabilities=${serviceInfo?.capabilities}).")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating serviceInfo", e)
        }

        // Register screen on/off & date change listener
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenAndDateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenAndDateReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Could not register screenAndDateReceiver", e)
        }

        // Sync daily reset immediately and keep local memory mirror of preferences
        serviceScope.launch(Dispatchers.IO) {
            checkAndPerformDailyReset()
            try {
                ProofOfWorkApp.instance.preferencesRepository.userPreferencesFlow.collect { prefs ->
                    cachedPreferences = prefs
                }
            } catch (e: Exception) {
                Log.e(TAG, "Preferences collection failed", e)
            }
        }

        ensureWatchdogRunning()
    }

    private suspend fun checkAndPerformDailyReset() {
        try {
            val app = ProofOfWorkApp.instance
            val didReset = app.preferencesRepository.syncDailyResetIfNeeded()
            if (didReset) {
                unflushedRemainderMs = 0L
                app.database.taskDao().resetDailyTasks()
                Log.i(TAG, "Daily reset performed for new day.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking daily reset", e)
        }
    }

    private fun getEffectivePreferences(): AppUserPreferences? {
        val repo = try {
            ProofOfWorkApp.instance.preferencesRepository
        } catch (_: Exception) {
            null
        }
        val base = repo?.latestPreferences ?: cachedPreferences ?: return null
        val todayStr = UserPreferencesRepository.getTodayDateString()
        return if (base.lastActiveDate.isNotEmpty() && base.lastActiveDate != todayStr) {
            // Day rolled over in memory; trigger async disk reset and return zeroed daily stats immediately
            serviceScope.launch(Dispatchers.IO) {
                checkAndPerformDailyReset()
            }
            base.copy(
                isGithubPushedToday = false,
                isLeetcodeSolvedToday = false,
                earnedFromTasksMinutes = 0,
                earnedFromVoiceMinutes = 0,
                voiceLogsTodayCount = 0,
                usedSecondsToday = 0,
                confessionUsedToday = false,
                lastActiveDate = todayStr
            )
        } else {
            base
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        ensureWatchdogRunning()

        // If the blocker overlay is actively displaying on screen, ignore background app events
        if (BlockerOverlayActivity.isShowing) {
            return
        }

        if (!isScreenInteractive()) {
            finalizeActiveSession(flushRemainder = true)
            return
        }

        val prefs = getEffectivePreferences() ?: return
        if (!prefs.isServiceEnabled) {
            finalizeActiveSession(flushRemainder = true)
            return
        }

        val eventPkg = event.packageName?.toString()
        val windowPkg = resolveForegroundAppPackage()

        // 1. If ProofOfWork itself is entering the foreground, flush immediately so UI shows exact time on frame 1
        if (windowPkg == applicationContext.packageName ||
            (eventPkg == applicationContext.packageName && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        ) {
            if (activeBlockedPackage != null) {
                Log.i(TAG, "Switched to ProofOfWork. Flushing active session immediately.")
                finalizeActiveSession(flushRemainder = true)
            }
            return
        }

        // 2. Check if the foreground window OR the interacting event belongs to a blocked package (e.g., Instagram)
        val matchedBlockedPkg = when {
            windowPkg != null && prefs.blockedPackages.contains(windowPkg) -> windowPkg
            eventPkg != null && prefs.blockedPackages.contains(eventPkg) && !isTransientSystemPackage(eventPkg) -> eventPkg
            else -> null
        }

        if (matchedBlockedPkg != null) {
            lastConfirmedBlockedEventMs = SystemClock.elapsedRealtime()
            handleBlockedAppActive(matchedBlockedPkg, prefs)
            return
        }

        // 3. If we are currently tracking a blocked app, check if the user genuinely switched to another app
        val currentBlocked = activeBlockedPackage
        if (currentBlocked != null) {
            // If the actual foreground window on screen is still the blocked app, ignore trailing background events
            // (e.g., com.android.launcher finishing its Recent Apps exit animation 180ms after tapping Instagram)
            if (windowPkg == currentBlocked) {
                return
            }

            val now = SystemClock.elapsedRealtime()
            val candidatePkg = windowPkg ?: eventPkg
            if (!candidatePkg.isNullOrBlank() &&
                candidatePkg != currentBlocked &&
                !isTransientSystemPackage(candidatePkg)
            ) {
                val isWindowSwitchEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                        event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                // Guard against trailing launcher cleanup events within 650ms of launching Instagram from Recent Apps
                val isRecentTransitionNoise = isLauncherPackage(candidatePkg) &&
                        (now - lastConfirmedBlockedEventMs < 650L)

                if (isWindowSwitchEvent && !isRecentTransitionNoise) {
                    Log.i(TAG, "User switched from $currentBlocked to $candidatePkg. Finalizing session.")
                    finalizeActiveSession(flushRemainder = true)
                }
            }
        }
    }

    /**
     * Continuous lightweight watchdog (runs every 500ms ONLY while screen is ON; 0% CPU when screen is OFF).
     * Guarantees accurate tracking even when resuming warm apps from Recent Apps or watching videos/Reels hands-free.
     */
    private fun ensureWatchdogRunning() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(500L)

                if (!isScreenInteractive()) {
                    if (activeBlockedPackage != null) {
                        finalizeActiveSession(flushRemainder = true)
                    }
                    // Sleep longer while screen is off until screen receiver wakes us
                    delay(2000L)
                    continue
                }

                if (BlockerOverlayActivity.isShowing) {
                    if (activeBlockedPackage != null) {
                        finalizeActiveSession(flushRemainder = true)
                    }
                    continue
                }

                val prefs = getEffectivePreferences() ?: continue
                if (!prefs.isServiceEnabled) {
                    if (activeBlockedPackage != null) {
                        finalizeActiveSession(flushRemainder = true)
                    }
                    continue
                }

                val fgPkg = resolveForegroundAppPackage()
                val currentBlocked = activeBlockedPackage

                if (fgPkg != null && prefs.blockedPackages.contains(fgPkg)) {
                    // Foreground window on screen is definitely Instagram (or another blocked package)
                    handleBlockedAppActive(fgPkg, prefs)
                    tickActiveSession(fgPkg)
                } else if (currentBlocked != null) {
                    val now = SystemClock.elapsedRealtime()
                    if (fgPkg != null && fgPkg != currentBlocked && !isTransientSystemPackage(fgPkg)) {
                        // A different real app (e.g. Launcher, ProofOfWork, WhatsApp) is now the settled foreground window
                        if (!isLauncherPackage(fgPkg) || (now - lastConfirmedBlockedEventMs >= 650L)) {
                            Log.i(TAG, "Watchdog detected foreground switch from $currentBlocked to $fgPkg.")
                            finalizeActiveSession(flushRemainder = true)
                        } else {
                            tickActiveSession(currentBlocked)
                        }
                    } else {
                        // fgPkg is null or transient overlay (e.g. volume bar / keyboard over Instagram); keep ticking
                        tickActiveSession(currentBlocked)
                    }
                }
            }
        }
    }

    private fun handleBlockedAppActive(packageName: String, prefs: AppUserPreferences) {
        if (BlockerOverlayActivity.isShowing) {
            return
        }

        if (!prefs.isWeekendFreedomActive && prefs.remainingSeconds <= 0) {
            Log.i(TAG, "Balance is 0 for $packageName. Triggering lock overlay.")
            triggerLockOverlay(packageName)
            return
        }

        if (activeBlockedPackage != packageName) {
            if (activeBlockedPackage != null) {
                finalizeActiveSession(flushRemainder = true)
            }
            activeBlockedPackage = packageName
            lastTickElapsedMs = SystemClock.elapsedRealtime()
            Log.i(TAG, "Started real-time tracking for $packageName (usedToday=${prefs.usedSecondsToday}s)")
        } else if (lastTickElapsedMs == 0L) {
            lastTickElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    private suspend fun tickActiveSession(packageName: String) {
        if (activeBlockedPackage != packageName) return
        val now = SystemClock.elapsedRealtime()
        if (lastTickElapsedMs == 0L) {
            lastTickElapsedMs = now
            return
        }

        val deltaMs = (now - lastTickElapsedMs).coerceIn(0L, 5000L)
        lastTickElapsedMs = now
        unflushedRemainderMs += deltaMs

        if (unflushedRemainderMs >= 1000L) {
            val secondsToDeduct = (unflushedRemainderMs / 1000L).toInt()
            unflushedRemainderMs %= 1000L

            persistUsedSeconds(secondsToDeduct)

            val updatedPrefs = getEffectivePreferences()
            if (updatedPrefs != null && !updatedPrefs.isWeekendFreedomActive && updatedPrefs.remainingSeconds <= 0) {
                Log.i(TAG, "Time expired during active $packageName session! Triggering lock overlay.")
                withContext(Dispatchers.Main) {
                    triggerLockOverlay(packageName)
                }
            }
        }
    }

    private fun finalizeActiveSession(flushRemainder: Boolean) {
        val pkg = activeBlockedPackage
        activeBlockedPackage = null

        val prevTick = lastTickElapsedMs
        lastTickElapsedMs = 0L

        if (pkg != null && flushRemainder) {
            val now = SystemClock.elapsedRealtime()
            if (prevTick > 0L) {
                val finalDeltaMs = (now - prevTick).coerceIn(0L, 2000L)
                unflushedRemainderMs += finalDeltaMs
            }

            // Flush any full seconds, plus round up if >= 500ms so short sessions & final fractional seconds aren't lost
            val wholeSeconds = (unflushedRemainderMs / 1000L).toInt()
            val remainderAfterWhole = unflushedRemainderMs % 1000L
            val extraSecond = if (remainderAfterWhole >= 500L) 1 else 0
            val totalSecondsToFlush = wholeSeconds + extraSecond

            if (extraSecond > 0) {
                unflushedRemainderMs = 0L
            } else {
                unflushedRemainderMs = remainderAfterWhole
            }

            if (totalSecondsToFlush > 0) {
                // Update in-memory snapshot synchronously (0ms latency) before persisting to disk
                cachedPreferences = cachedPreferences?.let { current ->
                    current.copy(usedSecondsToday = current.usedSecondsToday + totalSecondsToFlush)
                }
                serviceScope.launch(Dispatchers.IO + NonCancellable) {
                    persistUsedSeconds(totalSecondsToFlush)
                }
            }
            Log.i(TAG, "Session finalized for $pkg (flushed +${totalSecondsToFlush}s, remainder=${unflushedRemainderMs}ms)")
        }
    }

    private suspend fun persistUsedSeconds(seconds: Int) {
        if (seconds <= 0) return
        cachedPreferences = cachedPreferences?.let { current ->
            val todayStr = UserPreferencesRepository.getTodayDateString()
            val baseUsed = if (current.lastActiveDate != todayStr) 0 else current.usedSecondsToday
            current.copy(usedSecondsToday = baseUsed + seconds, lastActiveDate = todayStr)
        }
        try {
            ProofOfWorkApp.instance.preferencesRepository.addUsedSeconds(seconds)
            Log.i(TAG, "Instagram tick: +${seconds}s (used today: ${ getEffectivePreferences()?.usedSecondsToday }s)")
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting used seconds", e)
        }
    }

    /**
     * Inspects interactive application windows (both WindowManager title and root node)
     * and rootInActiveWindow to determine the true foreground app on screen.
     */
    private fun resolveForegroundAppPackage(): String? {
        try {
            val winList = windows
            if (!winList.isNullOrEmpty()) {
                val appWindows = winList.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                val activeAppWin = appWindows.firstOrNull { it.isFocused && it.isActive }
                    ?: appWindows.firstOrNull { it.isFocused }
                    ?: appWindows.firstOrNull { it.isActive }
                    ?: appWindows.firstOrNull()

                if (activeAppWin != null) {
                    // 1. Check WindowManager title directly (always populated on OxygenOS/Android 16 even when root is null!)
                    val winTitle = activeAppWin.title?.toString()?.trim()
                    if (!winTitle.isNullOrBlank()) {
                        when {
                            winTitle.equals("Instagram", ignoreCase = true) -> return "com.instagram.android"
                            winTitle.equals("ProofOfWork", ignoreCase = true) -> return applicationContext.packageName
                            winTitle.contains("Launcher", ignoreCase = true) -> return "com.android.launcher"
                        }
                    }

                    // 2. Check root node packageName
                    val pkg = activeAppWin.root?.packageName?.toString()
                    if (!pkg.isNullOrBlank() && !isTransientSystemPackage(pkg)) {
                        return pkg
                    }

                    // 3. Fallback to non-empty window title so any other foreground app (YouTube, WhatsApp, etc.) is recognized
                    if (!winTitle.isNullOrBlank()) {
                        return "window.title:${winTitle.lowercase(Locale.US)}"
                    }
                }
            }
        } catch (_: Exception) {
        }

        try {
            val rootPkg = rootInActiveWindow?.packageName?.toString()
            if (!rootPkg.isNullOrBlank() && !isTransientSystemPackage(rootPkg)) {
                return rootPkg
            }
        } catch (_: Exception) {
        }

        return null
    }

    private fun isScreenInteractive(): Boolean {
        return try {
            powerManager?.isInteractive ?: true
        } catch (_: Exception) {
            true
        }
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        val lower = pkg.lowercase(Locale.US)
        return lower.contains("launcher") || lower.contains("quickstep") || lower.contains("home")
    }

    /**
     * Packages that temporarily appear above an app (OnePlus/ColorOS system UI, keyboards,
     * notifications, volume sliders) which must NOT cancel or reset the active Instagram session.
     * Note: Launchers are NOT transient—they indicate the user pressed Home or opened Recent Apps!
     */
    private fun isTransientSystemPackage(pkg: String): Boolean {
        val lower = pkg.lowercase(Locale.US)
        if (isLauncherPackage(lower)) {
            return false
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
                lower.contains("toast") ||
                lower.contains("permissioncontroller")
    }

    private fun triggerLockOverlay(packageName: String) {
        finalizeActiveSession(flushRemainder = true)
        if (BlockerOverlayActivity.isShowing) {
            Log.i(TAG, "BlockerOverlayActivity is already showing. Suppressing launch.")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockedLaunchTime > 1500L) {
            lastBlockedLaunchTime = now
            launchBlockerOverlay(packageName)
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Ensure any active session is flushed and watchdog stays alive when app card is swiped from Recents
        ensureWatchdogRunning()
    }

    override fun onInterrupt() {
        finalizeActiveSession(flushRemainder = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        finalizeActiveSession(flushRemainder = true)
        watchdogJob?.cancel()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenAndDateReceiver)
            } catch (_: Exception) {
            }
        }
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AppBlockerService"
    }
}
