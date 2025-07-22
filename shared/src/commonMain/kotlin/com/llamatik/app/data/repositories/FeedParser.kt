package com.llamatik.app.data.repositories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlOtherAttributes
import nl.adaptivity.xmlutil.serialization.XmlSerialName

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
    @XmlElement val language: String,
    @XmlElement
    @XmlSerialName("updatePeriod", "http://purl.org/rss/1.0/modules/syndication/", "sy")
    val syUpdatePeriod: String,
    @XmlElement
    @XmlSerialName("updateFrequency", "http://purl.org/rss/1.0/modules/syndication/", "sy")
    val syUpdateFrequency: String,
    @XmlElement(true)
    val generator: String? = null,
    @XmlElement val image: FeedImage,
    @XmlElement(true)
    @XmlSerialName("link", "http://www.w3.org/2005/Atom", "atom")
    val atomLink: AtomLink? = null,
    val items: List<FeedItem>,
)

@Serializable
@XmlSerialName(value = "link", prefix = "atom")
data class AtomLink(
    @XmlOtherAttributes val href: String,
    @XmlOtherAttributes val rel: String,
    @XmlOtherAttributes val type: String
)

@Serializable
@SerialName("item")
data class FeedItem(
    @XmlElement val title: String,
    @XmlElement val link: String,
    @XmlElement val description: String,
    @XmlElement val pubDate: String,
    @XmlElement(true) val comments: String? = null,
    @XmlElement(true)
    @XmlSerialName("creator", "http://purl.org/dc/elements/1.1/", "dc")
    val dcCreator: String? = null,
    @XmlElement(true)
    @SerialName("category")
    val categories: List<String> = emptyList(),
    @XmlElement(true)
    val guid: String? = null,
    @XmlElement(true)
    @XmlSerialName("encoded", "http://purl.org/rss/1.0/modules/content/", "content")
    val contentEncoded: String? = null,
    @XmlElement(true)
    @XmlSerialName("comments", "http://purl.org/rss/1.0/modules/slash/", "slash")
    val slashComments: Int? = null,
    @XmlElement(true)
    @XmlSerialName("content", "http://search.yahoo.com/mrss/", "media")
    val mediaContent: List<MediaContent> = emptyList()
)

@Serializable
@SerialName("image")
data class FeedImage(
    @XmlElement val url: String,
    @XmlElement val title: String,
    @XmlElement val link: String,
    @XmlElement val width: String,
    @XmlElement val height: String
)

@Serializable
@XmlSerialName("thumbnail", "http://search.yahoo.com/mrss/", "media")
data class MediaThumbnail(
    @XmlOtherAttributes val url: String? = null,
    @XmlOtherAttributes val width: String? = null,
    @XmlOtherAttributes val height: String? = null
)

@Serializable
@XmlSerialName("content", "http://search.yahoo.com/mrss/", "media")
data class MediaContent(
    @XmlOtherAttributes val url: String? = null,
    @XmlOtherAttributes val type: String? = null,
    @XmlOtherAttributes val height: String? = null,
    @XmlOtherAttributes val width: String? = null,
    @XmlOtherAttributes val medium: String? = null,
    @XmlOtherAttributes val duration: String? = null,
    @XmlElement(true)
    @XmlSerialName("thumbnail", "http://search.yahoo.com/mrss/", "media")
    val thumbnail: MediaThumbnail? = null,
    @XmlElement(true)
    @XmlSerialName("credit", "http://search.yahoo.com/mrss/", "media")
    val credit: List<String> = emptyList(),
    @XmlElement(true)
    @XmlSerialName("text", "http://search.yahoo.com/mrss/", "media")
    val text: List<String> = emptyList()
)

@Serializable
@SerialName("enclosure")
data class FeedEnclosure(
    @XmlOtherAttributes val url: String?,
    @XmlOtherAttributes val length: String?,
    @XmlOtherAttributes val type: String?,
)
