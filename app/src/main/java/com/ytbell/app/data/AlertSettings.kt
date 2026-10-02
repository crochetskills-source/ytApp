package com.ytbell.app.data

import com.google.gson.annotations.SerializedName

data class AlertSettings(
    @SerializedName("checkIntervalMinutes")
    var checkIntervalMinutes: Int = 5, // 2, 5, 15, 30, 60 minutes
    
    @SerializedName("soundType")
    var soundType: String = SOUND_BELL, // BELL, CHIME, ALARM, SYSTEM_DEFAULT
    
    @SerializedName("loopAlarm")
    var loopAlarm: Boolean = true, // rings repeatedly until dismissed
    
    @SerializedName("overrideSilentMode")
    var overrideSilentMode: Boolean = true, // uses Alarm stream to break through silence/DND
    
    @SerializedName("vibrate")
    var vibrate: Boolean = true,
    
    @SerializedName("volumePercent")
    var volumePercent: Int = 100, // 0 to 100
    
    @SerializedName("wifiOnly")
    var wifiOnly: Boolean = false,
    
    @SerializedName("autoStartOnBoot")
    var autoStartOnBoot: Boolean = true,
    
    @SerializedName("foregroundServiceEnabled")
    var foregroundServiceEnabled: Boolean = true
) {
    companion object {
        const val SOUND_BELL = "BELL"
        const val SOUND_CHIME = "CHIME"
        const val SOUND_ALARM = "ALARM"
        const val SOUND_SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
    }
}
