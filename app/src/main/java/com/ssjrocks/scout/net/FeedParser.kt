package com.ssjrocks.scout.net

import com.ssjrocks.scout.data.Item
import com.ssjrocks.scout.data.Source
import com.ssjrocks.scout.data.SourceType
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Parses RSS 2.0, RSS 1.0 (RDF) and Atom, with extras for the source types Scout knows. */
object FeedParser {

    data class FeedInfo(val title: String?, val items: List<Item>)

    private const val MAX_ITEMS = 40

    fun parse(source: Source, xml: String, now: Long = System.currentTimeMillis()): FeedInfo {
        val doc = Jsoup.parse(xml, source.url, Parser.xmlParser())
        val root = doc.children().firstOrNull()
        val channelTitle = (root?.child("channel")?.childText("title") ?: root?.childText("title"))?.let(::clean)
        val entries = doc.getElementsByTag("item").ifEmpty { doc.getElementsByTag("entry") }
        val items = entries.take(MAX_ITEMS).mapNotNull { e ->
            runCatching { parseEntry(source, e, now) }.getOrNull()
        }
        return FeedInfo(channelTitle, items)
    }

    private fun parseEntry(source: Source, e: Element, now: Long): Item? {
        var title = clean(e.childText("title") ?: return null)
        var link = entryLink(e) ?: return null
        if (title.isBlank() || link.isBlank()) return null
        val published = parseDate(
            e.childText("pubDate") ?: e.childText("published") ?: e.childText("dc:date") ?: e.childText("updated")
                ?: e.childText("a10:updated"),
        )?.coerceAtMost(now) ?: now
        val html = e.child("content:encoded")?.wholeText() ?: e.child("content")?.wholeText()
            ?: e.child("description")?.wholeText() ?: e.child("summary")?.wholeText()
            ?: e.child("media:group")?.child("media:description")?.wholeText() ?: ""
        val body = if (html.isBlank()) null else Jsoup.parseBodyFragment(html, link)
        var summary = body?.text()?.let { truncate(clean(it), 300) }.orEmpty()
        var (image, small) = findImage(e, body)
        var author = (e.child("dc:creator") ?: e.child("author")?.let { it.child("name") ?: it })?.text()?.trim()?.ifBlank { null }
        var domain = hostOf(link)
        var publisher: String? = null
        var commentsUrl: String? = e.childText("comments")?.trim()?.ifBlank { null }
        var extUrl: String? = null
        var points: Int? = null
        var comments: Int? = null
        var videoId: String? = null
        var price: String? = null
        var expires: Long? = null

        when (source.type) {
            SourceType.YOUTUBE -> {
                videoId = e.childText("yt:videoId")?.trim()
                if (videoId != null) image = "https://i.ytimg.com/vi/$videoId/hq720.jpg"
                small = false
                points = e.child("media:group")?.child("media:community")?.child("media:statistics")?.attr("views")?.toIntOrNull()
                summary = truncate(summary, 200)
                author = null
            }
            SourceType.REDDIT -> {
                val anchors = body?.select("a[href]").orEmpty()
                val outside = anchors.firstOrNull { it.text().trim() == "[link]" }?.absUrl("href")
                commentsUrl = link
                author = author?.removePrefix("/u/")?.let { "u/$it" }
                val bigImage = sequenceOf(outside).filterNotNull().firstOrNull { hostOf(it) == "i.redd.it" }
                    ?: anchors.map { it.absUrl("href") }.firstOrNull { hostOf(it) == "preview.redd.it" && widthParam(it) >= 480 }
                if (bigImage != null) {
                    image = bigImage; small = false
                } else if (image != null) {
                    small = true
                }
                if (outside != null && !isRedditHost(hostOf(outside))) {
                    extUrl = outside
                    domain = hostOf(outside)
                } else {
                    domain = "reddit.com"
                }
                summary = body?.selectFirst("div.md")?.text()?.let { truncate(clean(it), 300) }.orEmpty()
            }
            SourceType.HN -> {
                val text = body?.text().orEmpty()
                points = Regex("Points: (\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
                comments = Regex("# Comments: (\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
                summary = ""
                image = null
            }
            SourceType.DEALS -> {
                val meta = e.child("ozb:meta")
                if (meta != null) {
                    points = (meta.attr("votes-pos").toIntOrNull() ?: 0) - (meta.attr("votes-neg").toIntOrNull() ?: 0)
                    comments = meta.attr("comment-count").toIntOrNull()
                    expires = parseDate(meta.attr("expiry").ifBlank { null })
                    meta.attr("image").ifBlank { null }?.let { image = it }
                    extUrl = meta.attr("url").ifBlank { null }
                    extUrl?.let { domain = hostOf(it) }
                }
                small = true
                price = Regex("(?:AU)?\\$\\s?\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?").find(title)?.value
                    ?: if (Regex("\\bfree\\b", RegexOption.IGNORE_CASE).containsMatchIn(title)) "Free" else null
                author = null
            }
            SourceType.RELEASES -> {
                val app = source.name.removeSuffix(" releases")
                if (!title.contains(app, ignoreCase = true)) title = "$app $title"
                summary = truncate(summary, 240)
            }
            SourceType.GNEWS -> {
                val src = e.child("source")
                publisher = src?.text()?.trim()?.ifBlank { null }
                src?.attr("url")?.ifBlank { null }?.let { domain = hostOf(it) }
                if (publisher != null && title.endsWith(" - $publisher")) title = title.removeSuffix(" - $publisher")
                summary = ""
                author = null
            }
            SourceType.NEWS, SourceType.STEAM -> {
                // Many feeds repeat the title (or "The post X appeared first on Y") as the summary.
                if (summary.startsWith(title)) summary = summary.removePrefix(title).trim()
                summary = summary.replace(Regex("The post .* appeared first on .*\\.?$"), "").trim()
            }
        }
        if (image != null && image!!.startsWith("//")) image = "https:$image"
        if (image != null && !image!!.startsWith("http")) image = null

        return Item(
            id = itemId(link),
            sourceId = source.id,
            title = title,
            link = link,
            summary = summary,
            image = image,
            imageSmall = small,
            author = author,
            published = published,
            fetched = now,
            domain = domain,
            publisher = publisher,
            commentsUrl = commentsUrl,
            extUrl = extUrl,
            points = points,
            comments = comments,
            videoId = videoId,
            price = price,
            expires = expires,
            enriched = source.type == SourceType.YOUTUBE || source.type == SourceType.DEALS ||
                (image != null && !small),
        )
    }

    private fun entryLink(e: Element): String? {
        val links = e.children().filter { it.tagName() == "link" }
        val atom = links.firstOrNull { it.hasAttr("href") && (it.attr("rel").isBlank() || it.attr("rel") == "alternate") }
            ?: links.firstOrNull { it.hasAttr("href") }
        if (atom != null) return atom.attr("href").trim()
        links.firstOrNull { it.text().isNotBlank() }?.let { return it.text().trim() }
        val guid = e.child("guid")
        if (guid != null && guid.attr("isPermaLink") != "false" && guid.text().startsWith("http")) return guid.text().trim()
        return e.child("id")?.text()?.trim()?.takeIf { it.startsWith("http") }
    }

    private fun findImage(e: Element, body: Element?): Pair<String?, Boolean> {
        // media:content, possibly several sizes, possibly inside media:group
        val media = (e.children().filter { it.tagName() == "media:content" } +
            (e.child("media:group")?.children()?.filter { it.tagName() == "media:content" } ?: emptyList()))
            .filter { m ->
                val url = m.attr("url")
                url.isNotBlank() && (m.attr("medium") == "image" || m.attr("type").startsWith("image") ||
                    Regex("\\.(jpe?g|png|webp|avif)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url))
            }
        media.maxByOrNull { it.attr("width").toIntOrNull() ?: 0 }?.let { m ->
            val w = m.attr("width").toIntOrNull()
            return m.attr("url") to (w != null && w < 300)
        }
        (e.child("media:thumbnail") ?: e.child("media:group")?.child("media:thumbnail"))?.let { t ->
            val w = t.attr("width").toIntOrNull()
            if (t.attr("url").isNotBlank()) return t.attr("url") to (w != null && w < 300)
        }
        e.children().firstOrNull { it.tagName() == "enclosure" && it.attr("type").startsWith("image") }?.let {
            return it.attr("url") to false
        }
        e.child("itunes:image")?.attr("href")?.ifBlank { null }?.let { return it to false }
        if (body != null) {
            for (img in body.select("img")) {
                val w = img.attr("width").toIntOrNull()
                val h = img.attr("height").toIntOrNull()
                if ((w != null && w < 50) || (h != null && h < 50)) continue
                val src = bestSrc(img) ?: continue
                if (Regex("feedburner|pixel|stats\\.wp|gravatar|emoji|/smilies/|doubleclick|feeds\\.|1x1").containsMatchIn(src)) continue
                return src to (w != null && w < 300)
            }
        }
        return null to false
    }

    private fun bestSrc(img: Element): String? {
        val srcset = img.attr("srcset").ifBlank { img.attr("data-srcset") }
        if (srcset.isNotBlank()) {
            val best = srcset.split(",").mapNotNull { part ->
                val bits = part.trim().split(Regex("\\s+"))
                val w = bits.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
                bits.firstOrNull()?.let { it to w }
            }.maxByOrNull { it.second }?.first
            if (best != null) return resolve(img.baseUri(), best)
        }
        val src = img.absUrl("src").ifBlank { img.absUrl("data-src") }.ifBlank { img.attr("src") }
        return src.ifBlank { null }
    }

    private fun resolve(base: String, rel: String): String =
        runCatching { URI(base).resolve(rel.trim()).toString() }.getOrDefault(rel)

    private fun widthParam(url: String): Int = Regex("[?&]width=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun isRedditHost(host: String) = host == "reddit.com" || host.endsWith(".reddit.com") || host == "redd.it" ||
        host.endsWith(".redd.it")

    // --- helpers -------------------------------------------------------------------------

    fun Element.child(name: String): Element? = children().firstOrNull { it.tagName().equals(name, ignoreCase = true) }
    private fun Element.childText(name: String): String? = child(name)?.wholeText()

    fun clean(s: String): String {
        var t = s.trim()
        if (t.contains('&') || t.contains('<')) t = Jsoup.parse(t).text()
        return t.replace(Regex("\\s+"), " ").trim()
    }

    fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.substring(0, max).substringBeforeLast(' ').trimEnd(',', '.', ';', ':') + "…"

    fun hostOf(url: String): String = runCatching {
        URI(url.trim().replace(" ", "%20")).host.orEmpty().lowercase().removePrefix("www.").removePrefix("m.")
            .removePrefix("amp.").removePrefix("old.").removePrefix("new.")
    }.getOrDefault("")

    private val trackingParams = Regex("^(utm_.*|fbclid|gclid|ocid|cmpid|sr_share|rss|ref|src|ito|mc_cid|mc_eid|cid|CMP|feature)$")

    fun normalizeUrl(url: String): String = runCatching {
        val u = URI(url.trim())
        val host = u.host?.lowercase()?.removePrefix("www.") ?: return url.trim()
        val query = u.rawQuery?.split('&')?.filter { p -> !trackingParams.matches(p.substringBefore('=')) }
            ?.joinToString("&")?.ifBlank { null }
        val path = (u.rawPath ?: "").trimEnd('/')
        "$host$path" + (query?.let { "?$it" } ?: "")
    }.getOrDefault(url.trim())

    fun itemId(link: String): String {
        val md = MessageDigest.getInstance("SHA-1").digest(normalizeUrl(link).toByteArray())
        return md.take(10).joinToString("") { "%02x".format(it) }
    }

    private val rfcPatterns = listOf(
        "EEE, d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm Z", "EEE, d MMM yyyy HH:mm zzz",
        "d MMM yyyy HH:mm:ss Z", "d MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd",
    )

    fun parseDate(s: String?): Long? {
        val t = s?.trim()?.ifBlank { null } ?: return null
        runCatching { return OffsetDateTime.parse(t).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return java.time.Instant.parse(t).toEpochMilli() }
        for (p in rfcPatterns) {
            runCatching {
                val f = SimpleDateFormat(p, Locale.US)
                if (!p.contains('Z') && !p.contains('z')) f.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return f.parse(t)?.time
            }
        }
        return null
    }
}
