package com.yxliu.keepalive

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.yxliu.keepalive.databinding.ActivityMainBinding

/**
 * Main screen for the KeepAlive app.
 *
 * Lets the user:
 *  - toggle whether the app keeps itself alive
 *  - browse installed apps and pick the ones to keep alive
 *  - start / stop monitoring (which starts the foreground service)
 *  - jump to the system accessibility settings to enable our service
 *  - request permission to ignore battery optimizations
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppListAdapter
    private lateinit var prefs: PreferenceHelper

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.toast_notification_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceHelper.getInstance(this)

        setupAppList()
        setupControls()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        loadInstalledApps()
    }

    private fun setupAppList() {
        adapter = AppListAdapter { app, checked ->
            val current = prefs.getTargetPackages().toMutableSet()
            if (checked) current.add(app.packageName) else current.remove(app.packageName)
            prefs.setTargetPackages(current)
        }
        binding.recyclerApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerApps.adapter = adapter
    }

    private fun setupControls() {
        // Keep self alive toggle
        binding.switchKeepSelf.setOnCheckedChangeListener(null)
        binding.switchKeepSelf.isChecked = prefs.isKeepSelfAlive()
        binding.switchKeepSelf.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            prefs.setKeepSelfAlive(checked)
        }

        // Auto start on boot toggle
        binding.switchAutoStart.setOnCheckedChangeListener(null)
        binding.switchAutoStart.isChecked = prefs.isAutoStartOnBoot()
        binding.switchAutoStart.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            prefs.setAutoStartOnBoot(checked)
        }

        // Start / stop monitoring
        binding.btnToggleMonitoring.setOnClickListener {
            val enabled = !prefs.isMonitoringEnabled()
            if (enabled) startMonitoring() else stopMonitoring()
        }

        // Open accessibility settings
        binding.btnOpenAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }

        // Battery optimizations
        binding.btnBatteryOpt.setOnClickListener {
            requestIgnoreBatteryOptimizations()
        }
    }

    private fun startMonitoring() {
        // Need notification permission on Android 13+ for the foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(perm)
            }
        }

        prefs.setMonitoringEnabled(true)
        KeepAliveForegroundService.start(this)
        refreshStatus()
    }

    private fun stopMonitoring() {
        prefs.setMonitoringEnabled(false)
        KeepAliveForegroundService.stop(this)
        refreshStatus()
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general battery optimization settings.
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun refreshStatus() {
        val monitoring = prefs.isMonitoringEnabled()
        binding.btnToggleMonitoring.setText(
            if (monitoring) R.string.stop_monitoring else R.string.start_monitoring
        )
        binding.tvStatus.text = getString(
            if (monitoring) R.string.status_monitoring else R.string.status_idle
        )
        val a11yEnabled = KeepAliveAccessibilityService.isServiceEnabled(this)
        binding.tvAccessibilityStatus.text = getString(
            if (a11yEnabled) R.string.accessibility_enabled else R.string.accessibility_disabled
        )
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val selected = prefs.getTargetPackages()

        val appInfos = allApps
            .filter { it.packageName != packageName } // do not show self
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null } // only launchable
            .sortedBy {
                pm.getApplicationLabel(it).toString().lowercase()
            }
            .map { ai: ApplicationInfo ->
                AppInfo(
                    packageName = ai.packageName,
                    appName = pm.getApplicationLabel(ai).toString(),
                    icon = pm.getApplicationIcon(ai),
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    selected = selected.contains(ai.packageName)
                )
            }

        adapter.submitList(appInfos)
        binding.tvAppCount.text = getString(R.string.app_count_fmt, appInfos.size)
    }
}
