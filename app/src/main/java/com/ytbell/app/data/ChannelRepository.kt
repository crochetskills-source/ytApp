package com.ytbell.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ChannelRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "yt_bell_notifier_prefs"
        private const val KEY_CHANNELS = "key_channels"
        private const val KEY_SETTINGS = "key_settings"
        private const val KEY_HISTORY = "key_history"
        private const val KEY_LAST_CHECK_TIME = "key_last_check_time"

        @Volatile
        private var instance: ChannelRepository? = null

        fun getInstance(context: Context): ChannelRepository {
            return instance ?: synchronized(this) {
                instance ?: ChannelRepository(context).also { instance = it }
            }
        }
    }

    @Synchronized
    fun getChannels(): MutableList<YouTubeChannel> {
        val json = prefs.getString(KEY_CHANNELS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<YouTubeChannel>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    fun saveChannels(channels: List<YouTubeChannel>) {
        val json = gson.toJson(channels)
        prefs.edit().putString(KEY_CHANNELS, json).apply()
    }

    @Synchronized
    fun addChannel(channel: YouTubeChannel): Boolean {
        val list = getChannels()
        if (list.any { it.id == channel.id }) {
            return false // Channel already exists
        }
        list.add(0, channel)
        saveChannels(list)
        return true
    }

    @Synchronized
    fun removeChannel(channelId: String) {
        val list = getChannels()
        list.removeAll { it.id == channelId }
        saveChannels(list)
    }

    @Synchronized
    fun updateChannel(channel: YouTubeChannel) {
        val list = getChannels()
        val index = list.indexOfFirst { it.id == channel.id }
        if (index != -1) {
            list[index] = channel
            saveChannels(list)
        }
    }

    @Synchronized
    fun setChannelEnabled(channelId: String, enabled: Boolean) {
        val list = getChannels()
        val channel = list.find { it.id == channelId }
        if (channel != null) {
            channel.isEnabled = enabled
            saveChannels(list)
        }
    }

    @Synchronized
    fun getSettings(): AlertSettings {
        val json = prefs.getString(KEY_SETTINGS, null) ?: return AlertSettings()
        return try {
            gson.fromJson(json, AlertSettings::class.java) ?: AlertSettings()
        } catch (e: Exception) {
            AlertSettings()
        }
    }

    @Synchronized
    fun saveSettings(settings: AlertSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString(KEY_SETTINGS, json).apply()
    }

    @Synchronized
    fun getHistory(): MutableList<AlertHistoryItem> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<AlertHistoryItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    fun addHistoryItem(item: AlertHistoryItem) {
        val history = getHistory()
        history.add(0, item)
        // Keep up to 100 history items
        if (history.size > 100) {
            history.subList(100, history.size).clear()
        }
        val json = gson.toJson(history)
        prefs.edit().putString(KEY_HISTORY, json).apply()
    }

    @Synchronized
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun setLastCheckTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK_TIME, timestamp).apply()
    }

    fun getLastCheckTime(): Long {
        return prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
    }
}
