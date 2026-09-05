package com.example.server

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.api.ChubApiClient
import com.example.data.db.AppDatabase
import com.example.data.repository.CharacterRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChubRepository
import com.example.data.repository.SettingsRepository
import com.example.util.CrashLogger

class DesktopServerService : Service() {

    companion object {
        const val ACTION_START = "com.example.server.ACTION_START"
        const val ACTION_STOP = "com.example.server.ACTION_STOP"
        const val EXTRA_PORT = "com.example.server.EXTRA_PORT"
        private const val NOTIFICATION_ID = 9081
        private const val CHANNEL_ID = "krizrp_desktop_server"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopServer()
            stopSelf()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
        startServer(port)
        return START_STICKY
    }

    private fun startServer(port: Int) {
        try {
            createNotificationChannel()

            val stopIntent = Intent(this, DesktopServerService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPendingIntent = PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openAppIntent = Intent(this, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val ip = DesktopServerManager.getLocalIpAddress(this)
            val serverUrl = "http://$ip:$port"

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Desktop Mode Active")
                .setContentText("Access from your PC at $serverUrl")
                .setContentIntent(openAppPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                CrashLogger.logError("DesktopServerService", "Foreground notification warning: ${e.message}", e)
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (_: Exception) {}
            }

            // Acquire Locks
            acquireLocks()

            // Initialize DB and Repositories
            val db = AppDatabase.getInstance(applicationContext)
            val characterRepository = CharacterRepository(db.characterDao())
            val chatRepository = ChatRepository(db.chatDao(), db.characterDao())
            val settingsRepository = SettingsRepository(db.settingDao())
            val chubApiClient = ChubApiClient()
            val chubRepository = ChubRepository(
                apiClient = chubApiClient,
                settingDao = db.settingDao(),
                characterRepository = characterRepository,
                context = applicationContext
            )

            DesktopServerManager.startHttpServer(
                context = applicationContext,
                port = port,
                db = db,
                characterRepository = characterRepository,
                chatRepository = chatRepository,
                settingsRepository = settingsRepository,
                chubRepository = chubRepository
            )
        } catch (e: Exception) {
            CrashLogger.logError("DesktopServerService", "Failed to start server service: ${e.message}", e)
            stopSelf()
        }
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KrizRP:DesktopServerWakeLock")?.apply {
                acquire(10 * 60 * 60 * 1000L) // 10 hours max
            }

            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "KrizRP:DesktopServerWifiLock")?.apply {
                acquire()
            }
        } catch (e: Exception) {
            CrashLogger.logError("DesktopServerService", "Failed acquiring wake/wifi locks: ${e.message}", e)
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null

            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
        } catch (_: Exception) {}
    }

    private fun stopServer() {
        DesktopServerManager.stopHttpServer()
        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Desktop Companion Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps KrizRP Desktop Mode running in the background"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }
}
