package com.example.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Starts the watchdog foreground service after the device has booted so the
 * keep-alive protection resumes automatically without the user opening the app.
 *
 * If the user has enabled "auto start on boot" we also flip the monitoring flag
 * back on and restore the previously saved target package selections, so the
 * accessibility service immediately picks them up and keeps the chosen apps alive.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = PreferenceHelper.getInstance(context)

        // When auto-start is enabled, resume monitoring on boot and re-apply the
        // saved target selections (they already persist in SharedPreferences, this
        // simply guarantees the monitoring flag is on so the watchdog runs).
        if (prefs.isAutoStartOnBoot()) {
            prefs.setMonitoringEnabled(true)
        }

        if (prefs.isMonitoringEnabled()) {
            val serviceIntent = Intent(context, KeepAliveForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
