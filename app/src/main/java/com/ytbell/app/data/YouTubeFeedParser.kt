package com.ytbell.app.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringReader

data class ParsedFeedResult(
    val channelId: String,
    val channelTitle: String,
    val latestVideo: LatestVideoItem?
)

data class LatestVideoItem(
    val videoId: String,
    val title: String,
    val published: String,
    val link: String
)

object YouTubeFeedParser {

    fun parse(xmlString: String): ParsedFeedResult? {
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(xmlString))

            var channelId = ""
            var channelTitle = ""
            var latestVideo: LatestVideoItem? = null

            var eventType = parser.eventType
            var inEntry = false
            var entryProcessed = false

            var curVideoId = ""
            var curTitle = ""
            var curPublished = ""
            var curLink = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when {
                            tag.equals("entry", ignoreCase = true) -> {
                                inEntry = true
                            }
                            !inEntry && tag.equals("title", ignoreCase = true) && channelTitle.isEmpty() -> {
                                channelTitle = readText(parser)
                            }
                            !inEntry && tag.equals("channelId", ignoreCase = true) && channelId.isEmpty() -> {
                                channelId = readText(parser)
                            }
                            inEntry && !entryProcessed -> {
                                when {
                                    tag.equals("videoId", ignoreCase = true) -> {
                                        curVideoId = readText(parser)
                                    }
                                    tag.equals("title", ignoreCase = true) -> {
                                        curTitle = readText(parser)
                                    }
                                    tag.equals("published", ignoreCase = true) -> {
                                        curPublished = readText(parser)
                                    }
                                    tag.equals("link", ignoreCase = true) -> {
                                        val rel = parser.getAttributeValue(null, "rel")
                                        val href = parser.getAttributeValue(null, "href")
                                        if (rel == "alternate" || rel == null) {
                                            curLink = href ?: ""
                                        }
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag.equals("entry", ignoreCase = true) && inEntry) {
                            if (!entryProcessed && curVideoId.isNotEmpty()) {
                                if (curLink.isEmpty()) {
                                    curLink = "https://www.youtube.com/watch?v=$curVideoId"
                                }
                                latestVideo = LatestVideoItem(
                                    videoId = curVideoId,
                                    title = curTitle,
                                    published = curPublished,
                                    link = curLink
                                )
                                entryProcessed = true
                            }
                            inEntry = false
                        }
                    }
                }
                eventType = parser.next()
            }

            if (channelId.isNotEmpty() || channelTitle.isNotEmpty()) {
                ParsedFeedResult(channelId, channelTitle, latestVideo)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text.trim()
            parser.nextTag()
        }
        return result
    }
}
