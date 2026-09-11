package com.yxliu.keepalive

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service. Kept minimal: it no longer performs any keep-alive
 * monitoring (Android 11+ restricts process visibility, so periodic polling cannot
 * see other apps). Restoring target apps after boot is handled by
 * [BootReceiver] + [KeepAliveForegroundService].
 *
 * The service still needs to be enabled if the user wants KeepAlive's process to
 * benefit from the elevated accessibility-service importance ranking.
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
        configureService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: no monitoring logic anymore.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Accessibility service destroyed")
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

    companion object {
        private const val TAG = "KeepA11y"

        /**
         * Convenience helper to check whether the accessibility service is enabled in
         * system settings. Used by the UI to show its status.
         */
        fun isServiceEnabled(context: Context): Boolean {
            // Check the system accessibility setting to confirm our service is enabled.
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
