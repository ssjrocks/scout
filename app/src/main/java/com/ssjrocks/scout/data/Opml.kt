package com.ssjrocks.scout.data

import com.ssjrocks.scout.net.Discover
import com.ssjrocks.scout.net.FeedParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser

/** OPML import/export, so sources can move to or from any other feed reader. */
object Opml {

    fun export(sources: List<Source>): String = buildString {
        fun esc(s: String) = Entities.escape(s)
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">\n<head><title>Scout sources</title></head>\n<body>\n")
        for (topic in Catalog.topics) {
            val inTopic = sources.filter { it.topic == topic.id && it.enabled }
            if (inTopic.isEmpty()) continue
            append("  <outline text=\"${esc(topic.name)}\" title=\"${esc(topic.name)}\">\n")
            for (s in inTopic) {
                val html = if (s.site.isNotBlank()) " htmlUrl=\"https://${esc(s.site)}\"" else ""
                append("    <outline type=\"rss\" text=\"${esc(s.name)}\" title=\"${esc(s.name)}\" xmlUrl=\"${esc(s.url)}\"$html/>\n")
            }
            append("  </outline>\n")
        }
        append("</body>\n</opml>\n")
    }

    /** Sources from an OPML file that aren't already in the list. */
    fun import(text: String, existing: List<Source>): List<Source> {
        val doc = Jsoup.parse(text, "", Parser.xmlParser())
        val known = existing.map { it.url }.toHashSet()
        return doc.select("outline[xmlUrl]").mapNotNull { o ->
            val url = o.attr("xmlUrl").trim()
            if (url.isBlank() || url in known) return@mapNotNull null
            known += url
            val name = o.attr("title").ifBlank { o.attr("text") }.ifBlank { FeedParser.hostOf(url) }
            val parentName = o.parent()?.takeIf { it.tagName() == "outline" && !it.hasAttr("xmlUrl") }?.attr("text").orEmpty()
            val topic = Catalog.topics.firstOrNull { it.name.equals(parentName, true) }?.id ?: Discover.guessTopic("$parentName $name")
            val host = FeedParser.hostOf(url)
            val type = when {
                FeedParser.isRedditHost(host) -> SourceType.REDDIT
                host == "youtube.com" -> SourceType.YOUTUBE
                host == "github.com" && url.endsWith("releases.atom") -> SourceType.RELEASES
                host.endsWith("steampowered.com") -> SourceType.STEAM
                host.endsWith("ozbargain.com.au") -> SourceType.DEALS
                host == "news.google.com" -> SourceType.GNEWS
                host == "hnrss.org" -> SourceType.HN
                else -> SourceType.NEWS
            }
            val site = FeedParser.hostOf(o.attr("htmlUrl")).ifBlank { host }
            Source("c_" + FeedParser.itemId(url).take(10), name, type, url, topic, site, custom = true)
        }
    }
}
