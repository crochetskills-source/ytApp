package com.ytbell.app.data

import com.google.gson.annotations.SerializedName

data class YouTubeChannel(
    @SerializedName("id")
    val id: String, // e.g. UCBJycsmduvYEL83R_U4JriQ
    
    @SerializedName("title")
    val title: String, // e.g. Marques Brownlee
    
    @SerializedName("handleOrUrl")
    val handleOrUrl: String, // original URL or @handle entered by user
    
    @SerializedName("lastVideoId")
    var lastVideoId: String? = null,
    
    @SerializedName("lastVideoTitle")
    var lastVideoTitle: String? = null,
    
    @SerializedName("lastVideoPublished")
    var lastVideoPublished: String? = null,
    
    @SerializedName("lastCheckedTime")
    var lastCheckedTime: Long = 0L,
    
    @SerializedName("isEnabled")
    var isEnabled: Boolean = true,
    
    @SerializedName("addedTimestamp")
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    fun getRssUrl(): String = "https://www.youtube.com/feeds/videos.xml?channel_id=$id"
    fun getChannelUrl(): String = "https://www.youtube.com/channel/$id"
}
