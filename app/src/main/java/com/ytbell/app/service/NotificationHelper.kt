package com.ytbell.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ytbell.app.data.YouTubeChannel
import com.ytbell.app.ui.MainActivity

object NotificationHelper {

    const val CHANNEL_SERVICE_ID = "yt_bell_service_channel"
    const val CHANNEL_ALERT_ID = "yt_bell_alert_channel"
    const val SERVICE_NOTIFICATION_ID = 1001
    const val ALERT_NOTIFICATION_ID_BASE = 2000

    const val ACTION_STOP_BELL = "com.ytbell.app.ACTION_STOP_BELL"
    const val ACTION_CHECK_NOW = "com.ytbell.app.ACTION_CHECK_NOW"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Background Service Channel
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that YouTube Video Bell is actively monitoring channels in background"
                setShowBadge(false)
            }

            // High Priority Alert Channel
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "New Video Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Plays bell alert and displays notification when a new video is published"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun buildServiceNotification(
        context: Context,
        channelCount: Int,
        lastCheckTime: String
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val checkNowIntent = Intent(context, YTMonitorForegroundService::class.java).apply {
            action = ACTION_CHECK_NOW
        }
        val checkNowPendingIntent = PendingIntent.getService(
            context,
            1,
            checkNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopBellIntent = Intent(context, YTMonitorForegroundService::class.java).apply {
            action = ACTION_STOP_BELL
        }
        val stopBellPendingIntent = PendingIntent.getService(
            context,
            2,
            stopBellIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (channelCount == 0) {
            "No channels added yet. Tap to add."
        } else {
            "Monitoring $channelCount channel(s) • Last checked: $lastCheckTime"
        }

        return NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setContentTitle("YouTube Bell Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_rotate, "Check Now", checkNowPendingIntent)
            .addAction(android.R.drawable.ic_lock_silent_mode, "Stop Bell", stopBellPendingIntent)
            .build()
    }

    fun showNewVideoAlert(
        context: Context,
        channel: YouTubeChannel,
        videoTitle: String,
        videoId: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val videoUrl = "https://www.youtube.com/watch?v=$videoId"
        val watchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val watchPendingIntent = PendingIntent.getActivity(
            context,
            videoId.hashCode(),
            watchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopBellIntent = Intent(context, YTMonitorForegroundService::class.java).apply {
            action = ACTION_STOP_BELL
        }
        val stopBellPendingIntent = PendingIntent.getService(
            context,
            videoId.hashCode() + 1,
            stopBellIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
            .setContentTitle("🔔 New Video: ${channel.title}")
            .setContentText(videoTitle)
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle("🔔 New Upload by ${channel.title}")
                .bigText(videoTitle)
                .setSummaryText("YouTube Bell Alert")
            )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(watchPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "Watch Video", watchPendingIntent)
            .addAction(android.R.drawable.ic_lock_silent_mode, "Stop Bell", stopBellPendingIntent)

        val notifId = ALERT_NOTIFICATION_ID_BASE + (channel.id.hashCode() % 1000)
        notificationManager.notify(notifId, builder.build())
    }
}
