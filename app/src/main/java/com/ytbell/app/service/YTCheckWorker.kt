package com.ytbell.app.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ytbell.app.audio.BellPlayer
import com.ytbell.app.data.AlertHistoryItem
import com.ytbell.app.data.ChannelRepository
import com.ytbell.app.data.YouTubeChannelResolver
import java.util.concurrent.TimeUnit

class YTCheckWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "yt_check_periodic_work"

        fun schedule(context: Context, intervalMinutes: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<YTCheckWorker>(
                intervalMinutes.coerceAtLeast(15),
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val repository = ChannelRepository.getInstance(appContext)
        val settings = repository.getSettings()
        val channels = repository.getChannels()

        for (channel in channels) {
            if (!channel.isEnabled) continue

            val result = YouTubeChannelResolver.checkChannelFeed(channel)
            if (result.success && result.latestVideo != null) {
                val latest = result.latestVideo
                channel.lastCheckedTime = System.currentTimeMillis()

                if (channel.lastVideoId != null && latest.videoId != channel.lastVideoId) {
                    channel.lastVideoId = latest.videoId
                    channel.lastVideoTitle = latest.title
                    channel.lastVideoPublished = latest.published

                    repository.addHistoryItem(
                        AlertHistoryItem(
                            channelId = channel.id,
                            channelTitle = channel.title,
                            videoId = latest.videoId,
                            videoTitle = latest.title,
                            videoUrl = latest.link
                        )
                    )

                    BellPlayer.play(appContext, settings)
                    NotificationHelper.showNewVideoAlert(
                        appContext,
                        channel,
                        latest.title,
                        latest.videoId
                    )
                } else if (channel.lastVideoId == null) {
                    channel.lastVideoId = latest.videoId
                    channel.lastVideoTitle = latest.title
                    channel.lastVideoPublished = latest.published
                }
            }
        }

        repository.setLastCheckTime(System.currentTimeMillis())
        repository.saveChannels(channels)

        return Result.success()
    }
}
