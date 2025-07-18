package com.dcshub.app.data.repositories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlOtherAttributes

class FeedParser {
    fun parse(xml: String): List<FeedItem> {
        val obj = XML.decodeFromString<ArticlesFeed>(xml)
        return obj.channel.items
    }
}

@Serializable
@SerialName("rss")
data class ArticlesFeed(
    @XmlOtherAttributes val version: String,
    val channel: RssChannel
)

@Serializable
@SerialName("channel")
data class RssChannel(
    @XmlElement val title: String,
    @XmlElement val link: String,
    @XmlElement val description: String,
    @XmlElement val lastBuildDate: String,
    @XmlElement val ttl: String,
    val items: List<FeedItem>
)

@Serializable
@SerialName("item")
data class FeedItem(
    @XmlElement val title: String,
    @XmlElement val link: String,
    @XmlElement val description: String,
    @XmlElement val enclosure: FeedEnclosure?,
    @XmlElement val pubDate: String
)

@Serializable
@SerialName("enclosure")
data class FeedEnclosure(
    @XmlOtherAttributes val url: String?,
    @XmlOtherAttributes val length: String?,
    @XmlOtherAttributes val type: String?,
)
