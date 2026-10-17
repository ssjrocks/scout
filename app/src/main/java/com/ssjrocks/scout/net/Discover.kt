package com.ssjrocks.scout.net

import com.ssjrocks.scout.data.Catalog
import com.ssjrocks.scout.data.Source
import com.ssjrocks.scout.data.SourceType
import org.jsoup.Jsoup
import java.net.URI

/**
 * Turns whatever the user typed or shared (a site, a feed URL, r/sub, a YouTube channel, a GitHub
 * repo, a Steam game, or plain keywords) into a source, and checks it actually returns stories.
 */
object Discover {

    data class Found(val source: Source, val sampleTitles: List<String>)

    private const val FEED_ACCEPT = "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5"

    suspend fun resolve(inputRaw: String, topic: String): Found {
        val input = inputRaw.trim().removeSuffix("/")
        val id = "c_" + FeedParser.itemId(input + System.currentTimeMillis()).take(10)

        Regex("^(?:https?://)?(?:www\\.|old\\.|m\\.)?(?:reddit\\.com/)?/?r/([A-Za-z0-9_]{2,30})").find(input)?.let { m ->
            val sub = m.groupValues[1]
            return check(Source(id, "r/$sub", SourceType.REDDIT, Catalog.redditUrl(sub), topic, "reddit.com", custom = true))
        }
        if (input.startsWith("@") || input.contains("youtube.com/") || input.contains("youtu.be/")) {
            return check(youtube(id, input, topic))
        }
        Regex("github\\.com/([\\w.-]+)/([\\w.-]+)").find(input)?.let { m ->
            val repo = m.groupValues[1] + "/" + m.groupValues[2].removeSuffix(".git")
            val name = m.groupValues[2].replaceFirstChar { it.uppercase() } + " releases"
            return check(Source(id, name, SourceType.RELEASES, "https://github.com/$repo/releases.atom", topic, "github.com", weight = 0.8f, custom = true))
        }
        Regex("store\\.steampowered\\.com/app/(\\d+)(?:/([^/?]+))?").find(input)?.let { m ->
            val name = m.groupValues.getOrNull(2)?.replace('_', ' ')?.ifBlank { null } ?: "Steam app ${m.groupValues[1]}"
            return check(Source(id, name, SourceType.STEAM, "https://store.steampowered.com/feeds/news/app/${m.groupValues[1]}/", topic, "steampowered.com", weight = 0.8f, custom = true))
        }
        val looksLikeUrl = input.startsWith("http") || (!input.contains(' ') && Regex("^[\\w-]+(\\.[\\w-]+)+(/.*)?$").matches(input))
        if (!looksLikeUrl) {
            return check(Source(id, "Google News · $input", SourceType.GNEWS, Catalog.googleNewsUrl("$input when:7d"), topic, "news.google.com", custom = true))
        }
        val url = if (input.startsWith("http")) input else "https://$input"
        return check(website(id, url, topic))
    }

    private suspend fun youtube(id: String, input: String, topic: String): Source {
        Regex("(UC[\\w-]{22})").find(input)?.let { m ->
            val channelId = m.groupValues[1]
            val name = runCatching { FeedParser.parse(stub(channelId), Http.get(Catalog.youtubeUrl(channelId))).title }.getOrNull()
            return Source(id, name ?: "YouTube channel", SourceType.YOUTUBE, Catalog.youtubeUrl(channelId), topic, "youtube.com", custom = true)
        }
        val page = when {
            input.startsWith("@") -> "https://www.youtube.com/$input"
            input.startsWith("http") -> input
            else -> "https://$input"
        }
        val html = Http.get(page, accept = "text/html")
        val channelId = Regex("\"externalId\":\"(UC[\\w-]{22})\"").find(html)?.groupValues?.get(1)
            ?: Regex("channel_id=(UC[\\w-]{22})").find(html)?.groupValues?.get(1)
            ?: Regex("\"channelId\":\"(UC[\\w-]{22})\"").find(html)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Couldn't find that YouTube channel")
        val name = Jsoup.parse(html).selectFirst("meta[property=og:title]")?.attr("content")?.ifBlank { null } ?: "YouTube channel"
        return Source(id, name, SourceType.YOUTUBE, Catalog.youtubeUrl(channelId), topic, "youtube.com", custom = true)
    }

    private fun stub(channelId: String) =
        Source("x", "x", SourceType.YOUTUBE, Catalog.youtubeUrl(channelId), "tech")

    private fun looksLikeFeed(body: String): Boolean {
        val head = body.take(2000).lowercase()
        return head.contains("<rss") || head.contains("<feed") || head.contains("<rdf:rdf")
    }

    private suspend fun website(id: String, url: String, topic: String): Source {
        val host = FeedParser.hostOf(url)
        val type = when {
            host.endsWith("ozbargain.com.au") -> SourceType.DEALS
            host == "hnrss.org" || host == "news.ycombinator.com" -> SourceType.HN
            else -> SourceType.NEWS
        }
        if (host == "news.ycombinator.com") {
            return Source(id, "Hacker News", SourceType.HN, "https://hnrss.org/frontpage?points=100", topic, host, custom = true)
        }
        val body = Http.get(url, accept = "$FEED_ACCEPT, text/html")
        if (looksLikeFeed(body)) {
            val probe = Source(id, host, type, url, topic, host, custom = true)
            val title = FeedParser.parse(probe, body).title?.ifBlank { null }
            return probe.copy(name = title ?: host)
        }
        val doc = Jsoup.parse(body, url)
        val siteName = doc.selectFirst("meta[property=og:site_name]")?.attr("content")?.ifBlank { null }
            ?: doc.title().substringBefore(" | ").substringBefore(" - ").trim().ifBlank { host }
        val advertised = doc.select("link[rel=alternate][type*=rss], link[rel=alternate][type*=atom]")
            .map { it.absUrl("href") }.filter { it.isNotBlank() && !it.contains("comments") }
        val guesses = listOf("/feed", "/rss", "/rss.xml", "/feed.xml", "/atom.xml", "/index.xml", "/feed/", "/news/rss")
            .map { URI(url).resolve(it).toString() }
        for (candidate in (advertised + guesses).distinct()) {
            val text = runCatching { Http.get(candidate, accept = FEED_ACCEPT) }.getOrNull() ?: continue
            if (looksLikeFeed(text)) {
                return Source(id, siteName, type, candidate, topic, host, custom = true)
            }
        }
        throw IllegalArgumentException("No feed found on $host. Try its RSS link, or type keywords to follow them as a Google News search.")
    }

    private suspend fun check(src: Source): Found {
        val xml = Http.get(src.url, accept = FEED_ACCEPT)
        val parsed = FeedParser.parse(src, xml)
        if (parsed.items.isEmpty()) throw IllegalArgumentException("That feed has no stories right now.")
        return Found(src, parsed.items.take(3).map { it.title })
    }

    /** Best guess at a topic for a new source, from its name. */
    fun guessTopic(name: String): String {
        val lower = name.lowercase()
        return Catalog.topics.firstOrNull { t -> t.keywords.any { k -> Regex("(?<![\\w])" + Regex.escape(k) + "(?![\\w])").containsMatchIn(lower) } }?.id
            ?: "tech"
    }
}
