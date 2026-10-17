package com.ssjrocks.scout.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

@Serializable
data class Block(val kind: String, val text: String = "", val url: String? = null)

@Serializable
data class Article(
    val url: String,
    val title: String,
    val site: String,
    val byline: String? = null,
    val image: String? = null,
    val blocks: List<Block> = emptyList(),
    val minutes: Int = 1,
)

/** Reader view: pulls the article text out of a page (a small Readability-style heuristic). */
object Reader {

    suspend fun fetch(url: String): Article {
        val html = Http.get(url, accept = "text/html,application/xhtml+xml")
        return withContext(Dispatchers.Default) { extract(url, html) }
    }

    private val junk = listOf(
        "script", "style", "noscript", "svg", "form", "button", "nav", "header", "footer", "aside", "iframe",
        "[role=navigation]", "[role=banner]", "[role=complementary]", "[aria-hidden=true]",
        "[class~=(?i)(share|social|related|newsletter|advert|promo|subscribe|sidebar|comment|recommend|popular|trending|breadcrumb|cookie|paywall|signup|outbrain|taboola)]",
        "[id~=(?i)(comment|sidebar|related|newsletter|cookie|footer|header|nav)]",
    )

    fun extract(url: String, html: String): Article {
        val doc = Jsoup.parse(html, url)
        fun meta(vararg sel: String) = sel.firstNotNullOfOrNull { s -> doc.selectFirst(s)?.attr("content")?.trim()?.ifBlank { null } }
        val title = meta("meta[property=og:title]", "meta[name=twitter:title]") ?: doc.title().ifBlank { url }
        val site = meta("meta[property=og:site_name]") ?: FeedParser.hostOf(url)
        val image = doc.selectFirst("meta[property=og:image]")?.absUrl("content")?.ifBlank { null }
        val byline = meta("meta[name=author]", "meta[property=article:author]")?.takeIf { !it.startsWith("http") }
            ?: doc.selectFirst("[rel=author], .byline, .author-name, [itemprop=author]")?.text()?.trim()?.take(80)?.ifBlank { null }

        // Class-name matches are loose (WordPress puts "comments-open" on <body>), so never remove
        // page containers or anything that holds several paragraphs.
        val keep = setOf("html", "body", "main", "article")
        for (sel in junk) runCatching {
            doc.select(sel).filter { it.tagName() !in keep && it.select("p").size < 3 }.forEach { it.remove() }
        }

        val root = doc.selectFirst("[itemprop=articleBody]")
            ?: bestCandidate(doc.body() ?: doc)
            ?: doc.body()
        val blocks = ArrayList<Block>()
        if (root != null) walk(root, blocks)
        var text = blocks.filter { it.kind == "p" }.sumOf { it.text.length }
        if (text < 400) {
            blocks.clear()
            doc.select("p").forEach { p -> p.text().trim().takeIf { it.length > 30 }?.let { blocks += Block("p", it) } }
            text = blocks.sumOf { it.text.length }
        }
        // Drop a leading image that repeats the share image, and repeated title heading.
        val cleaned = blocks.filterIndexed { i, b ->
            !(i < 3 && b.kind == "img" && b.url == image) && !(i < 3 && b.kind == "h" && b.text.equals(title, ignoreCase = true))
        }.distinctBy { it.kind + it.text + it.url }
        val words = cleaned.sumOf { b -> if (b.kind == "img") 0 else b.text.split(' ').size }
        return Article(url, title, site, byline, image, cleaned, maxOf(1, words / 220))
    }

    private fun bestCandidate(body: Element): Element? {
        val scores = HashMap<Element, Double>()
        for (p in body.select("p")) {
            val len = p.text().length
            if (len < 40) continue
            val parent = p.parent() ?: continue
            scores[parent] = (scores[parent] ?: 0.0) + len
            parent.parent()?.let { gp -> scores[gp] = (scores[gp] ?: 0.0) + len / 2.0 }
        }
        return scores.entries.maxByOrNull { (el, s) ->
            val textLen = el.text().length.coerceAtLeast(1)
            val linkLen = el.select("a").sumOf { it.text().length }
            s * (1 - linkLen.toDouble() / textLen)
        }?.key
    }

    private fun walk(el: Element, out: MutableList<Block>) {
        for (node in el.childNodes()) {
            if (node is TextNode) {
                val t = node.text().trim()
                if (t.length > 60) out += Block("p", t)
                continue
            }
            val c = node as? Element ?: continue
            when (c.tagName().lowercase()) {
                "p" -> {
                    c.select("img").forEach { img -> imageBlock(img)?.let(out::add) }
                    c.text().trim().takeIf { it.isNotEmpty() }?.let { out += Block("p", it) }
                }
                "h1", "h2", "h3", "h4", "h5" -> c.text().trim().takeIf { it.isNotEmpty() }?.let { out += Block("h", it) }
                "blockquote" -> c.text().trim().takeIf { it.isNotEmpty() }?.let { out += Block("quote", it) }
                "pre" -> c.wholeText().trimEnd().takeIf { it.isNotEmpty() }?.let { out += Block("code", it) }
                "ul", "ol" -> c.children().filter { it.tagName() == "li" }.forEach { li ->
                    li.text().trim().takeIf { it.isNotEmpty() }?.let { out += Block("li", it) }
                }
                "img" -> imageBlock(c)?.let(out::add)
                "figure", "picture" -> {
                    (c.selectFirst("img"))?.let { img -> imageBlock(img)?.let(out::add) }
                    c.selectFirst("figcaption")?.text()?.trim()?.ifBlank { null }?.let { out += Block("caption", it) }
                }
                "table" -> c.text().trim().takeIf { it.isNotEmpty() }?.let { out += Block("p", it) }
                else -> walk(c, out)
            }
        }
    }

    private fun imageBlock(img: Element): Block? {
        val w = img.attr("width").toIntOrNull()
        if (w != null && w < 120) return null
        val src = listOf("data-src", "data-lazy-src", "data-original", "src").firstNotNullOfOrNull { a ->
            img.absUrl(a).ifBlank { null }?.takeIf { !it.startsWith("data:") }
        } ?: return null
        if (Regex("pixel|spacer|blank\\.gif|1x1|logo|avatar|icon").containsMatchIn(src)) return null
        return Block("img", img.attr("alt").trim(), src)
    }
}
