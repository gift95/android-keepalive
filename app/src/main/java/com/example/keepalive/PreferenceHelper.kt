package com.example.keepalive

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the user's keep-alive configuration in SharedPreferences.
 *
 * Maintains two pieces of state:
 *   - whether the app should also keep itself alive
 *   - the set of package names that should be kept alive
 */
class PreferenceHelper private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setKeepSelfAlive(value: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SELF, value).apply()
    }

    fun isKeepSelfAlive(): Boolean = prefs.getBoolean(KEY_KEEP_SELF, false)

    fun setTargetPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_TARGET_PACKAGES, packages).apply()
    }

    fun getTargetPackages(): Set<String> =
        prefs.getStringSet(KEY_TARGET_PACKAGES, emptySet()) ?: emptySet()

    fun setMonitoringEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()
    }

    fun isMonitoringEnabled(): Boolean = prefs.getBoolean(KEY_MONITORING_ENABLED, false)

    fun setAutoStartOnBoot(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START, value).apply()
    }

    fun isAutoStartOnBoot(): Boolean = prefs.getBoolean(KEY_AUTO_START, false)

    companion object {
        private const val PREFS_NAME = "keep_alive_prefs"
        private const val KEY_KEEP_SELF = "keep_self_alive"
        private const val KEY_TARGET_PACKAGES = "target_packages"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_AUTO_START = "auto_start_on_boot"

        @Volatile private var instance: PreferenceHelper? = null

        fun getInstance(context: Context): PreferenceHelper =
            instance ?: synchronized(this) {
                instance ?: PreferenceHelper(context).also { instance = it }
            }
    }
}
