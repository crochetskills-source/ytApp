package com.ytbell.app.data

import com.google.gson.annotations.SerializedName

data class AlertHistoryItem(
    @SerializedName("id")
    val id: String = java.util.UUID.randomUUID().toString(),
    
    @SerializedName("channelId")
    val channelId: String,
    
    @SerializedName("channelTitle")
    val channelTitle: String,
    
    @SerializedName("videoId")
    val videoId: String,
    
    @SerializedName("videoTitle")
    val videoTitle: String,
    
    @SerializedName("videoUrl")
    val videoUrl: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
