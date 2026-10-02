package com.ytbell.app.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.ytbell.app.audio.BellPlayer
import com.ytbell.app.data.ChannelRepository
import com.ytbell.app.data.YouTubeChannel
import com.ytbell.app.data.YouTubeChannelResolver
import com.ytbell.app.databinding.ActivityMainBinding
import com.ytbell.app.R
import com.ytbell.app.service.NotificationHelper
import com.ytbell.app.service.YTMonitorForegroundService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ChannelRepository
    private lateinit var channelAdapter: ChannelAdapter

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Notification permission is recommended so you receive video alerts!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val channelUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadChannels()
            updateStatusHeader()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ChannelRepository.getInstance(this)
        NotificationHelper.createNotificationChannels(this)

        requestNotificationPermission()
        setupUI()
        setupListeners()
        loadChannels()
        updateStatusHeader()

        // Auto-start foreground service if enabled
        val settings = repository.getSettings()
        if (settings.foregroundServiceEnabled && !YTMonitorForegroundService.isServiceRunning) {
            val hasEnabledChannels = repository.getChannels().any { it.isEnabled }
            if (hasEnabledChannels) {
                YTMonitorForegroundService.startService(this)
            }
        }

        // Register Bell State listener to toggle top banner
        BellPlayer.setOnPlayingStateListener { isPlaying ->
            runOnUiThread {
                binding.bannerBellRinging.visibility = if (isPlaying) View.VISIBLE else View.GONE
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupUI() {
        channelAdapter = ChannelAdapter(
            context = this,
            channels = mutableListOf(),
            onToggleEnabled = { channel, isEnabled ->
                repository.setChannelEnabled(channel.id, isEnabled)
                updateStatusHeader()
            },
            onDelete = { channel ->
                repository.removeChannel(channel.id)
                loadChannels()
                updateStatusHeader()
                Snackbar.make(binding.root, "Removed \"${channel.title}\"", Snackbar.LENGTH_SHORT).show()
            }
        )

        binding.rvChannels.layoutManager = LinearLayoutManager(this)
        binding.rvChannels.adapter = channelAdapter
    }

    private fun setupListeners() {
        // Toolbar actions
        binding.btnCheckNow.setOnClickListener {
            performManualRefresh()
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, AlertHistoryActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Pull to refresh
        binding.swipeRefresh.setColorSchemeResources(com.ytbell.app.R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            performManualRefresh()
        }

        // Add channel FAB
        binding.fabAddChannel.setOnClickListener {
            val dialog = AddChannelBottomSheetDialog {
                loadChannels()
                updateStatusHeader()
                // If service is running, perform quick check on the newly added channel
                if (YTMonitorForegroundService.isServiceRunning) {
                    val intent = Intent(this, YTMonitorForegroundService::class.java).apply {
                        action = YTMonitorForegroundService.ACTION_CHECK_NOW
                    }
                    startService(intent)
                }
            }
            dialog.show(supportFragmentManager, "AddChannelDialog")
        }

        // Stop Bell button on Banner
        binding.btnStopBell.setOnClickListener {
            BellPlayer.stop(this)
            binding.bannerBellRinging.visibility = View.GONE
        }

        // Background service switch
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            val settings = repository.getSettings()
            settings.foregroundServiceEnabled = isChecked
            repository.saveSettings(settings)

            if (isChecked) {
                YTMonitorForegroundService.startService(this)
                binding.tvServiceStatus.text = getString(com.ytbell.app.R.string.service_active)
            } else {
                YTMonitorForegroundService.stopService(this)
                binding.tvServiceStatus.text = getString(com.ytbell.app.R.string.service_paused)
            }
        }
    }

    private fun loadChannels() {
        val channels = repository.getChannels()
        if (channels.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvChannels.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvChannels.visibility = View.VISIBLE
            channelAdapter.updateList(channels)
        }
    }

    private fun updateStatusHeader() {
        val channels = repository.getChannels()
        val enabledCount = channels.count { it.isEnabled }
        binding.tvChannelCount.text = "$enabledCount of ${channels.size} Channels Active"

        val settings = repository.getSettings()
        binding.tvNextCheck.text = "Checking every ${settings.checkIntervalMinutes} mins • Low data RSS"

        val lastCheckTime = repository.getLastCheckTime()
        if (lastCheckTime > 0) {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            binding.tvLastCheckedTime.text = "Last checked: ${sdf.format(Date(lastCheckTime))}"
        } else {
            binding.tvLastCheckedTime.text = "Not checked yet"
        }

        binding.switchService.isChecked = YTMonitorForegroundService.isServiceRunning || settings.foregroundServiceEnabled
        binding.tvServiceStatus.text = if (binding.switchService.isChecked) {
            getString(com.ytbell.app.R.string.service_active)
        } else {
            getString(com.ytbell.app.R.string.service_paused)
        }

        binding.bannerBellRinging.visibility = if (BellPlayer.isCurrentlyPlaying()) View.VISIBLE else View.GONE
    }

    private fun performManualRefresh() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val channels = repository.getChannels()
            var anyFound = false
            val settings = repository.getSettings()

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
                        anyFound = true

                        BellPlayer.play(this@MainActivity, settings)
                        NotificationHelper.showNewVideoAlert(
                            this@MainActivity,
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

            binding.swipeRefresh.isRefreshing = false
            loadChannels()
            updateStatusHeader()

            if (anyFound) {
                Toast.makeText(this@MainActivity, "🔔 New video detected!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "All channels up to date", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadChannels()
        updateStatusHeader()

        val filter = IntentFilter().apply {
            addAction(YTMonitorForegroundService.BROADCAST_CHANNELS_UPDATED)
            addAction(YTMonitorForegroundService.BROADCAST_NEW_VIDEO_ALERT)
        }
        ContextCompat.registerReceiver(
            this,
            channelUpdateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(channelUpdateReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
}
