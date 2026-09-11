package com.yxliu.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * Foreground service. Two roles:
 *  1. Keeps the host process alive when the user enables "keep self alive".
 *  2. After device boot, restores the user-selected target apps: each package is
 *     launched up to [MAX_RESTORE_ATTEMPTS] times (with a delay between tries),
 *     then the service exits unless self-protection is enabled.
 */
class KeepAliveForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.i(TAG, "Foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_BOOT_RESTORE -> {
                restoreTargetsAfterBoot()
                // If the user did not enable self-protection, exit once the restore
                // attempt finishes; otherwise keep running as a foreground service.
                if (!PreferenceHelper.getInstance(this).isKeepSelfAlive()) {
                    handler.postDelayed({
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }, RESTORE_FINISH_GRACE_MS)
                }
            }
        }
        return START_STICKY // system should restart us if killed
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "Foreground service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Attempts to launch every target package, up to [MAX_RESTORE_ATTEMPTS] tries
     * per package with [RESTORE_RETRY_DELAY_MS] between tries. Runs on a background
     * thread so the main thread is never blocked.
     */
    private fun restoreTargetsAfterBoot() {
        val targets = PreferenceHelper.getInstance(this).getTargetPackages()
        if (targets.isEmpty()) {
            Log.i(TAG, "Boot restore: no target packages")
            return
        }
        Thread {
            for (pkg in targets) {
                var launched = false
                for (attempt in 1..MAX_RESTORE_ATTEMPTS) {
                    if (launchPackage(pkg, attempt)) {
                        launched = true
                        break
                    }
                    try {
                        Thread.sleep(RESTORE_RETRY_DELAY_MS)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                if (!launched) {
                    Log.w(TAG, "Boot restore: gave up on $pkg after $MAX_RESTORE_ATTEMPTS attempts")
                }
            }
        }.start()
    }

    private fun launchPackage(packageName: String, attempt: Int): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: run {
                    Log.w(TAG, "Boot restore: no launch intent for $packageName")
                    return false
                }
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            startActivity(intent)
            Log.i(TAG, "Boot restore: launched $packageName (attempt $attempt)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Boot restore: failed to launch $packageName", e)
            false
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "KeepAliveFgs"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "keep_alive_channel"

        // How many times each package is tried after boot, and the gap between tries.
        const val MAX_RESTORE_ATTEMPTS = 3
        private const val RESTORE_RETRY_DELAY_MS = 3_000L
        // Grace period before the service shuts down after the restore attempt.
        private const val RESTORE_FINISH_GRACE_MS = 2_000L

        const val ACTION_STOP = "com.yxliu.keepalive.action.STOP"
        const val ACTION_BOOT_RESTORE = "com.yxliu.keepalive.action.BOOT_RESTORE"

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Starts the boot-restore routine: launches every saved target package up to
         * [MAX_RESTORE_ATTEMPTS] times. Called from [BootReceiver] after BOOT_COMPLETED.
         */
        fun startBootRestore(context: Context) {
            val intent = Intent(context, KeepAliveForegroundService::class.java).apply {
                action = ACTION_BOOT_RESTORE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveForegroundService::class.java)
            intent.action = ACTION_STOP
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
