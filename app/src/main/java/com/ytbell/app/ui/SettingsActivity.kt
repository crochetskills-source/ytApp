package com.ytbell.app.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ytbell.app.audio.BellPlayer
import com.ytbell.app.data.AlertSettings
import com.ytbell.app.data.ChannelRepository
import com.ytbell.app.databinding.ActivitySettingsBinding
import com.ytbell.app.service.YTMonitorForegroundService

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: ChannelRepository
    private var isTestingSound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ChannelRepository.getInstance(this)
        val settings = repository.getSettings()

        setupToolbar()
        loadSettingsIntoUI(settings)
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbarSettings.setNavigationOnClickListener {
            stopTestSoundIfPlaying()
            finish()
        }
    }

    private fun loadSettingsIntoUI(settings: AlertSettings) {
        // Sound Type
        when (settings.soundType) {
            AlertSettings.SOUND_CHIME -> binding.rbSoundChime.isChecked = true
            AlertSettings.SOUND_ALARM -> binding.rbSoundAlarm.isChecked = true
            AlertSettings.SOUND_SYSTEM_DEFAULT -> binding.rbSoundSystem.isChecked = true
            else -> binding.rbSoundBell.isChecked = true
        }

        // Loop alarm & Silent mode
        binding.switchLoopAlarm.isChecked = settings.loopAlarm
        binding.switchOverrideSilent.isChecked = settings.overrideSilentMode
        binding.switchVibrate.isChecked = settings.vibrate

        // Volume
        binding.sbVolume.progress = settings.volumePercent
        binding.tvVolumePercent.text = "${settings.volumePercent}%"

        // Interval
        when (settings.checkIntervalMinutes) {
            2 -> binding.rb2Min.isChecked = true
            15 -> binding.rb15Min.isChecked = true
            30 -> binding.rb30Min.isChecked = true
            else -> binding.rb5Min.isChecked = true
        }

        // Network & Boot
        binding.switchWifiOnly.isChecked = settings.wifiOnly
        binding.switchAutoBoot.isChecked = settings.autoStartOnBoot
    }

    private fun setupListeners() {
        // Volume SeekBar listener
        binding.sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvVolumePercent.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Test Sound Button
        binding.btnTestSound.setOnClickListener {
            if (isTestingSound) {
                stopTestSoundIfPlaying()
            } else {
                startTestSound()
            }
        }

        // Save Button
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun getCurrentSettingsFromUI(): AlertSettings {
        val soundType = when {
            binding.rbSoundChime.isChecked -> AlertSettings.SOUND_CHIME
            binding.rbSoundAlarm.isChecked -> AlertSettings.SOUND_ALARM
            binding.rbSoundSystem.isChecked -> AlertSettings.SOUND_SYSTEM_DEFAULT
            else -> AlertSettings.SOUND_BELL
        }

        val interval = when {
            binding.rb2Min.isChecked -> 2
            binding.rb15Min.isChecked -> 15
            binding.rb30Min.isChecked -> 30
            else -> 5
        }

        return AlertSettings(
            checkIntervalMinutes = interval,
            soundType = soundType,
            loopAlarm = binding.switchLoopAlarm.isChecked,
            overrideSilentMode = binding.switchOverrideSilent.isChecked,
            vibrate = binding.switchVibrate.isChecked,
            volumePercent = binding.sbVolume.progress,
            wifiOnly = binding.switchWifiOnly.isChecked,
            autoStartOnBoot = binding.switchAutoBoot.isChecked,
            foregroundServiceEnabled = repository.getSettings().foregroundServiceEnabled
        )
    }

    private fun startTestSound() {
        val testSettings = getCurrentSettingsFromUI().copy(
            loopAlarm = false // In test mode, play once so it doesn't disturb indefinitely
        )
        isTestingSound = true
        binding.btnTestSound.text = "⏹  Stop Playing"
        BellPlayer.play(this, testSettings)

        // Reset button after 3 seconds
        binding.btnTestSound.postDelayed({
            if (isTestingSound) {
                stopTestSoundIfPlaying()
            }
        }, 3500)
    }

    private fun stopTestSoundIfPlaying() {
        isTestingSound = false
        BellPlayer.stop(this)
        binding.btnTestSound.text = "▶  Test Sound Now"
    }

    private fun saveSettings() {
        stopTestSoundIfPlaying()
        val settings = getCurrentSettingsFromUI()
        repository.saveSettings(settings)

        // Restart service to pick up new check frequency if running
        if (YTMonitorForegroundService.isServiceRunning) {
            YTMonitorForegroundService.stopService(this)
            YTMonitorForegroundService.startService(this)
        }

        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        stopTestSoundIfPlaying()
        super.onDestroy()
    }
}
