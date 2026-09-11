package com.yxliu.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After the device boots, restores the saved keep-alive configuration: if monitoring
 * is enabled, asks [KeepAliveForegroundService] to launch every target package
 * (up to 3 attempts each). No periodic monitoring is performed.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = PreferenceHelper.getInstance(context)

        // When auto-start is enabled, resume the monitoring flag on boot so the
        // restore routine runs even if the user stopped monitoring before rebooting.
        if (prefs.isAutoStartOnBoot()) {
            prefs.setMonitoringEnabled(true)
        }

        if (prefs.isMonitoringEnabled()) {
            KeepAliveForegroundService.startBootRestore(context)
        }
    }
}
