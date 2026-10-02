package com.ytbell.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ytbell.app.data.AlertSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin

object BellPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var loopJob: Job? = null
    private var isPlaying = false
    private var onPlayingStateChangedListener: ((Boolean) -> Unit)? = null

    fun setOnPlayingStateListener(listener: ((Boolean) -> Unit)?) {
        this.onPlayingStateChangedListener = listener
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying

    @Synchronized
    fun play(context: Context, settings: AlertSettings) {
        stop(context)
        isPlaying = true
        onPlayingStateChangedListener?.invoke(true)

        // Start Vibration if enabled
        if (settings.vibrate) {
            startVibration(context, settings.loopAlarm)
        }

        when (settings.soundType) {
            AlertSettings.SOUND_SYSTEM_DEFAULT -> {
                playSystemRingtone(context, settings)
            }
            AlertSettings.SOUND_ALARM -> {
                playSystemAlarm(context, settings)
            }
            AlertSettings.SOUND_CHIME -> {
                playSynthesizedTone(context, settings, toneType = 1)
            }
            else -> {
                // SOUND_BELL (default)
                playSynthesizedTone(context, settings, toneType = 0)
            }
        }
    }

    @Synchronized
    fun stop(context: Context? = null) {
        isPlaying = false
        loopJob?.cancel()
        loopJob = null

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        context?.let { stopVibration(it) }
        onPlayingStateChangedListener?.invoke(false)
    }

    private fun playSystemRingtone(context: Context, settings: AlertSettings) {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alertUri)
                setAudioAttributes(createAudioAttributes(settings.overrideSilentMode))
                isLooping = settings.loopAlarm
                val volume = (settings.volumePercent / 100.0f).coerceIn(0f, 1f)
                setVolume(volume, volume)
                setOnCompletionListener {
                    if (!settings.loopAlarm) {
                        stop(context)
                    }
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to synthesized bell
            playSynthesizedTone(context, settings, toneType = 0)
        }
    }

    private fun playSystemAlarm(context: Context, settings: AlertSettings) {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alertUri)
                setAudioAttributes(createAudioAttributes(overrideSilent = true))
                isLooping = settings.loopAlarm
                val volume = (settings.volumePercent / 100.0f).coerceIn(0f, 1f)
                setVolume(volume, volume)
                setOnCompletionListener {
                    if (!settings.loopAlarm) {
                        stop(context)
                    }
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playSynthesizedTone(context, settings, toneType = 0)
        }
    }

    /**
     * High-fidelity bell synthesizer: generates realistic multi-harmonic chime or brass desk-bell.
     * toneType 0: Service Bell (Harmonics at 1200Hz, 2400Hz, 3600Hz with exponential decay)
     * toneType 1: Gentle Chime (Two-tone melody: 880Hz then 1320Hz)
     */
    private fun playSynthesizedTone(context: Context, settings: AlertSettings, toneType: Int) {
        val sampleRate = 44100
        val durationSeconds = if (toneType == 0) 1.8 else 2.2
        val numSamples = (sampleRate * durationSeconds).toInt()
        val sample = ShortArray(numSamples)

        val volumeFactor = (settings.volumePercent / 100.0).coerceIn(0.0, 1.0) * Short.MAX_VALUE

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var wave = 0.0

            if (toneType == 0) {
                // Classic Bell Sound: fundamental + rich bell overtones + sharp strike decay
                val decay = exp(-3.2 * t)
                val strike = exp(-25.0 * t) * 0.4
                val fundamental = sin(2.0 * Math.PI * 1480.0 * t)
                val overtone1 = sin(2.0 * Math.PI * 2960.0 * t) * 0.4
                val overtone2 = sin(2.0 * Math.PI * 4150.0 * t) * 0.25
                val overtone3 = sin(2.0 * Math.PI * 5920.0 * t) * 0.15

                wave = (fundamental + overtone1 + overtone2 + overtone3) * decay + strike
            } else {
                // Two-tone Chime: Ding-Dong (880Hz for 0.8s, then 1175Hz for 1.4s)
                if (t < 0.8) {
                    val decay1 = exp(-2.5 * t)
                    wave = sin(2.0 * Math.PI * 880.0 * t) * decay1 * 0.8 +
                            sin(2.0 * Math.PI * 1760.0 * t) * decay1 * 0.3
                } else {
                    val t2 = t - 0.8
                    val decay2 = exp(-2.0 * t2)
                    wave = sin(2.0 * Math.PI * 1174.66 * t2) * decay2 * 0.9 +
                            sin(2.0 * Math.PI * 2349.32 * t2) * decay2 * 0.35
                }
            }

            sample[i] = (wave.coerceIn(-1.0, 1.0) * volumeFactor).toInt().toShort()
        }

        val usage = if (settings.overrideSilentMode) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
        val streamType = if (settings.overrideSilentMode) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION

        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(numSamples * 2, minBufferSize)

        loopJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                do {
                    val audioTrack = AudioTrack.Builder()
                        .setAudioAttributes(attributes)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()

                    audioTrack.write(sample, 0, sample.size)
                    audioTrack.play()

                    // Wait for tone duration
                    delay((durationSeconds * 1000).toLong() + 300)

                    audioTrack.stop()
                    audioTrack.release()

                    if (settings.loopAlarm && isPlaying) {
                        // Pause 1 second between rings
                        delay(1000)
                    }
                } while (settings.loopAlarm && isActive && isPlaying)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (!settings.loopAlarm) {
                    stop(context)
                }
            }
        }
    }

    private fun createAudioAttributes(overrideSilent: Boolean): AudioAttributes {
        val usage = if (overrideSilent) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
        return AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private fun startVibration(context: Context, loop: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val timings = longArrayOf(0, 400, 200, 400, 800)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0)
            val repeatIndex = if (loop) 0 else -1

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, repeatIndex)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, repeatIndex)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
