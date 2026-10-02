package com.ytbell.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.os.PowerManager
import com.ytbell.app.audio.BellPlayer
import com.ytbell.app.data.AlertHistoryItem
import com.ytbell.app.data.ChannelRepository
import com.ytbell.app.data.YouTubeChannelResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class YTMonitorForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var monitorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var repository: ChannelRepository

    companion object {
        const val ACTION_START = "com.ytbell.app.ACTION_START"
        const val ACTION_STOP = "com.ytbell.app.ACTION_STOP"
        const val ACTION_CHECK_NOW = "com.ytbell.app.ACTION_CHECK_NOW"
        const val ACTION_STOP_BELL = "com.ytbell.app.ACTION_STOP_BELL"
        const val BROADCAST_CHANNELS_UPDATED = "com.ytbell.app.BROADCAST_CHANNELS_UPDATED"
        const val BROADCAST_NEW_VIDEO_ALERT = "com.ytbell.app.BROADCAST_NEW_VIDEO_ALERT"

        var isServiceRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, YTMonitorForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, YTMonitorForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = ChannelRepository.getInstance(applicationContext)
        NotificationHelper.createNotificationChannels(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "YTVideoBell::MonitorWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                isServiceRunning = false
                stopMonitoring()
                BellPlayer.stop(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_BELL -> {
                BellPlayer.stop(this)
            }
            ACTION_CHECK_NOW -> {
                serviceScope.launch {
                    performCheck()
                }
            }
            ACTION_START -> {
                if (!isServiceRunning) {
                    isServiceRunning = true
                    val notif = NotificationHelper.buildServiceNotification(
                        this,
                        repository.getChannels().filter { it.isEnabled }.size,
                        getCurrentTimeString()
                    )
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        startForeground(
                            NotificationHelper.SERVICE_NOTIFICATION_ID,
                            notif,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notif)
                    }
                    startMonitoringLoop()
                }
            }
        }

        return START_STICKY
    }

    private fun startMonitoringLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive && isServiceRunning) {
                performCheck()

                val settings = repository.getSettings()
                val intervalMs = (settings.checkIntervalMinutes * 60 * 1000L).coerceAtLeast(60_000L)
                delay(intervalMs)
            }
        }
    }

    private suspend fun performCheck() {
        wakeLock?.acquire(30_000L) // 30 sec max wake
        try {
            val settings = repository.getSettings()

            // Wi-Fi check if user enabled wifi-only mode
            if (settings.wifiOnly && !isWifiConnected()) {
                return
            }

            val channels = repository.getChannels()
            var anyUpdated = false

            for (channel in channels) {
                if (!channel.isEnabled) continue

                val result = YouTubeChannelResolver.checkChannelFeed(channel)
                if (result.success && result.latestVideo != null) {
                    val latest = result.latestVideo
                    channel.lastCheckedTime = System.currentTimeMillis()

                    if (result.channelTitle != null && result.channelTitle.isNotEmpty()) {
                        // Keep channel title updated if changed
                    }

                    // Check if new video!
                    if (channel.lastVideoId != null && latest.videoId != channel.lastVideoId) {
                        channel.lastVideoId = latest.videoId
                        channel.lastVideoTitle = latest.title
                        channel.lastVideoPublished = latest.published
                        anyUpdated = true

                        // Log history item
                        val historyItem = AlertHistoryItem(
                            channelId = channel.id,
                            channelTitle = channel.title,
                            videoId = latest.videoId,
                            videoTitle = latest.title,
                            videoUrl = latest.link
                        )
                        repository.addHistoryItem(historyItem)

                        // Trigger Bell & Notification!
                        BellPlayer.play(applicationContext, settings)
                        NotificationHelper.showNewVideoAlert(
                            applicationContext,
                            channel,
                            latest.title,
                            latest.videoId
                        )

                        // Notify UI
                        val alertIntent = Intent(BROADCAST_NEW_VIDEO_ALERT).apply {
                            putExtra("channelTitle", channel.title)
                            putExtra("videoTitle", latest.title)
                            putExtra("videoId", latest.videoId)
                        }
                        sendBroadcast(alertIntent)
                    } else if (channel.lastVideoId == null) {
                        // First time seeing this channel: save baseline latest video without ringing
                        channel.lastVideoId = latest.videoId
                        channel.lastVideoTitle = latest.title
                        channel.lastVideoPublished = latest.published
                        anyUpdated = true
                    }
                }
            }

            repository.setLastCheckTime(System.currentTimeMillis())
            repository.saveChannels(channels)

            // Update foreground service notification status
            val enabledCount = channels.count { it.isEnabled }
            val updatedNotif = NotificationHelper.buildServiceNotification(
                this,
                enabledCount,
                getCurrentTimeString()
            )
            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notifManager.notify(NotificationHelper.SERVICE_NOTIFICATION_ID, updatedNotif)

            // Send broadcast to update UI list
            val updateIntent = Intent(BROADCAST_CHANNELS_UPDATED)
            sendBroadcast(updateIntent)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun isWifiConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun getCurrentTimeString(): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    override fun onDestroy() {
        isServiceRunning = false
        stopMonitoring()
        BellPlayer.stop(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
