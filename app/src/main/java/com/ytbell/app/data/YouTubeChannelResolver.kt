package com.ytbell.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ResolutionResult(
    val success: Boolean,
    val channel: YouTubeChannel? = null,
    val latestVideo: LatestVideoItem? = null,
    val errorMessage: String? = null
)

data class FeedCheckResult(
    val success: Boolean,
    val hasNewVideo: Boolean = false,
    val latestVideo: LatestVideoItem? = null,
    val channelTitle: String? = null,
    val errorMessage: String? = null
)

object YouTubeChannelResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val CHANNEL_ID_PATTERN = Pattern.compile("UC[a-zA-Z0-9_-]{22}")
    private val RSS_LINK_PATTERN = Pattern.compile("https://www\\.youtube\\.com/feeds/videos\\.xml\\?channel_id=(UC[a-zA-Z0-9_-]{22})")
    private val BROWSE_ID_PATTERN = Pattern.compile("\"browseId\"\\s*:\\s*\"(UC[a-zA-Z0-9_-]{22})\"")
    private val META_CHANNEL_ID_PATTERN = Pattern.compile("<meta\\s+itemprop=\"channelId\"\\s+content=\"(UC[a-zA-Z0-9_-]{22})\"")

    suspend fun resolveInput(rawInput: String): ResolutionResult = withContext(Dispatchers.IO) {
        val input = rawInput.trim()
        if (input.isEmpty()) {
            return@withContext ResolutionResult(false, errorMessage = "Please enter a YouTube channel link or @handle.")
        }

        var channelId: String? = null

        // 1. Direct channel ID check
        if (input.matches(Regex("^UC[a-zA-Z0-9_-]{22}$"))) {
            channelId = input
        }

        // 2. Direct channel link containing /channel/UC...
        if (channelId == null && input.contains("/channel/")) {
            val matcher = CHANNEL_ID_PATTERN.matcher(input)
            if (matcher.find()) {
                channelId = matcher.group()
            }
        }

        // 3. Needs network resolution (e.g. @handle or vanity URL)
        if (channelId == null) {
            val targetUrl = when {
                input.startsWith("@") -> "https://www.youtube.com/$input"
                input.startsWith("http://") || input.startsWith("https://") -> input
                input.contains("youtube.com") -> "https://$input"
                else -> "https://www.youtube.com/@$input"
            }

            channelId = resolveChannelIdFromWebPage(targetUrl)
        }

        if (channelId == null) {
            return@withContext ResolutionResult(
                false,
                errorMessage = "Could not locate YouTube channel from this link. Make sure the channel link or @handle is valid."
            )
        }

        // Fetch RSS feed to verify and get channel details + latest video
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        try {
            val request = Request.Builder()
                .url(feedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ResolutionResult(
                        false,
                        errorMessage = "YouTube feed returned error code: ${response.code}"
                    )
                }
                val body = response.body?.string() ?: ""
                val parsed = YouTubeFeedParser.parse(body)
                    ?: return@withContext ResolutionResult(
                        false,
                        errorMessage = "Could not parse channel feed. The channel might have no uploads yet or is private."
                    )

                val channel = YouTubeChannel(
                    id = channelId,
                    title = parsed.channelTitle.ifEmpty { "YouTube Channel" },
                    handleOrUrl = input,
                    lastVideoId = parsed.latestVideo?.videoId,
                    lastVideoTitle = parsed.latestVideo?.title,
                    lastVideoPublished = parsed.latestVideo?.published,
                    lastCheckedTime = System.currentTimeMillis()
                )

                ResolutionResult(
                    success = true,
                    channel = channel,
                    latestVideo = parsed.latestVideo
                )
            }
        } catch (e: Exception) {
            ResolutionResult(false, errorMessage = "Connection failed: ${e.localizedMessage}")
        }
    }

    private fun resolveChannelIdFromWebPage(url: String): String? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null

                // Look for RSS feed link
                val rssMatcher = RSS_LINK_PATTERN.matcher(html)
                if (rssMatcher.find()) {
                    return rssMatcher.group(1)
                }

                // Look for meta channelId
                val metaMatcher = META_CHANNEL_ID_PATTERN.matcher(html)
                if (metaMatcher.find()) {
                    return metaMatcher.group(1)
                }

                // Look for browseId
                val browseMatcher = BROWSE_ID_PATTERN.matcher(html)
                if (browseMatcher.find()) {
                    return browseMatcher.group(1)
                }

                // Fallback direct UC match
                val ucMatcher = CHANNEL_ID_PATTERN.matcher(html)
                if (ucMatcher.find()) {
                    return ucMatcher.group()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun checkChannelFeed(channel: YouTubeChannel): FeedCheckResult = withContext(Dispatchers.IO) {
        val feedUrl = channel.getRssUrl()
        try {
            val request = Request.Builder()
                .url(feedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext FeedCheckResult(
                        success = false,
                        errorMessage = "HTTP ${response.code}"
                    )
                }

                val xml = response.body?.string() ?: ""
                val parsed = YouTubeFeedParser.parse(xml)
                    ?: return@withContext FeedCheckResult(
                        success = false,
                        errorMessage = "Empty or unparseable feed"
                    )

                val newVideo = parsed.latestVideo
                val hasNew = if (newVideo != null && channel.lastVideoId != null) {
                    newVideo.videoId != channel.lastVideoId
                } else false

                FeedCheckResult(
                    success = true,
                    hasNewVideo = hasNew,
                    latestVideo = newVideo,
                    channelTitle = parsed.channelTitle
                )
            }
        } catch (e: Exception) {
            FeedCheckResult(false, errorMessage = e.localizedMessage)
        }
    }
}
