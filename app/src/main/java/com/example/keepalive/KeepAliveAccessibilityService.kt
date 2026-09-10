package com.example.keepalive

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Accessibility service that keeps selected applications alive.
 *
 * Strategy:
 *  1. Listens for [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] events to know which
 *     package is currently in the foreground.
 *  2. Periodically (every [CHECK_INTERVAL_MS]) checks whether each target package is
 *     still running. If a target is found missing it is restarted via its launch intent.
 *  3. Optionally keeps the host app (this one) alive by starting the
 *     [KeepAliveForegroundService] if it has been killed.
 *
 * Because the system grants accessibility services a high importance ranking, this
 * service process is rarely killed, making it a good watchdog.
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val restartCounts = mutableMapOf<String, Int>()
    private var lastCheckElapsed: Long = 0L

    private val checkRunnable = object : Runnable {
        override fun run() {
            performKeepAliveCheck()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
        configureService()
        handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We mostly use the periodic polling, but we also capture window state changes
        // to react more quickly when a target app is killed and the foreground switches.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            // Quickly verify the package that just left the foreground.
            handler.removeCallbacks(checkRunnable)
            handler.post(checkRunnable)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        Log.i(TAG, "Accessibility service destroyed")
        // Schedule a restart of self by sending broadcast to the foreground service
        // (which the system usually keeps alive).
        try {
            val intent = Intent(this, KeepAliveForegroundService::class.java)
            intent.action = KeepAliveForegroundService.ACTION_RESTART_ACCESSIBILITY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request accessibility restart", e)
        }
    }

    private fun configureService() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 200
        }
        serviceInfo = info
    }

    /**
     * Inspects the running processes and restarts any target package that is no longer
     * running. Also restarts the foreground service if self-protection is enabled.
     */
    private fun performKeepAliveCheck() {
        // Avoid running too often when events fire repeatedly.
        val now = SystemClock.elapsedRealtime()
        if (now - lastCheckElapsed < MIN_CHECK_GAP_MS) return
        lastCheckElapsed = now

        val prefs = PreferenceHelper.getInstance(this)
        if (!prefs.isMonitoringEnabled()) return

        val targets = prefs.getTargetPackages().toMutableSet()
        if (prefs.isKeepSelfAlive()) {
            targets.add(packageName) // ensure this app is included
        }
        if (targets.isEmpty()) return

        val runningPackages = getRunningPackages()

        for (pkg in targets) {
            if (pkg == packageName) {
                ensureSelfAlive()
                continue
            }
            if (!runningPackages.contains(pkg)) {
                restartApp(pkg)
            }
        }
    }

    private fun getRunningPackages(): Set<String> {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return emptySet()
        val processes = am.runningAppProcesses ?: return emptySet()
        return processes.mapNotNull { it.processName?.substringBefore(':') }.toSet()
    }

    private fun restartApp(packageName: String) {
        val attempt = (restartCounts[packageName] ?: 0) + 1
        restartCounts[packageName] = attempt
        if (attempt > MAX_RESTART_ATTEMPTS) {
            // Give up after repeated failures to avoid hammering the system.
            Log.w(TAG, "Max restart attempts reached for $packageName, skipping")
            return
        }

        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                Log.w(TAG, "No launch intent for $packageName; cannot restart")
                return
            }
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            startActivity(intent)
            Log.i(TAG, "Restarted target package: $packageName (attempt $attempt)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart $packageName", e)
        }
    }

    private fun ensureSelfAlive() {
        try {
            val intent = Intent(this, KeepAliveForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    companion object {
        private const val TAG = "KeepA11y"
        private const val CHECK_INTERVAL_MS = 15_000L
        private const val MIN_CHECK_GAP_MS = 5_000L
        private const val MAX_RESTART_ATTEMPTS = 5

        /**
         * Convenience helper to check whether the accessibility service is enabled in
         * system settings. Used by the UI to prompt the user to enable it.
         */
        fun isServiceEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            val enabled = am.runningServices.size
            // Use Settings to confirm our specific service is enabled.
            val service = "${context.packageName}/${KeepAliveAccessibilityService::class.java.name}"
            val settingValue = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val enabledServices = settingValue.split(':').filter { it.isNotBlank() }
            // Compare by simple class name (services may include the flattened component name)
            return enabledServices.any { it.equals(service, ignoreCase = true) || it.endsWith("/${KeepAliveAccessibilityService::class.java.name}", ignoreCase = true) }
        }
    }
}
