package com.ssjrocks.scout.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup

/** Fills in what feeds leave out: a picture for the card, and the real link behind Google News. */
object Enrich {

    /** The page's share image (og:image / twitter:image). Reads only the first 400 KB. */
    suspend fun ogImage(url: String): String? {
        val html = Http.get(url, accept = "text/html,application/xhtml+xml", maxBytes = 400_000)
        val doc = Jsoup.parse(html, url)
        val candidates = listOf(
            "meta[property=og:image:secure_url]", "meta[property=og:image]", "meta[name=og:image]",
            "meta[name=twitter:image]", "meta[name=twitter:image:src]", "meta[property=twitter:image]",
        )
        for (sel in candidates) {
            val el = doc.selectFirst(sel) ?: continue
            val v = el.absUrl("content").ifBlank { el.attr("content") }.trim()
            if (v.startsWith("http") && !v.contains("default", ignoreCase = true) && !v.endsWith(".svg")) return v
        }
        return doc.selectFirst("link[rel=image_src]")?.absUrl("href")?.ifBlank { null }
    }

    /**
     * Google News RSS links point at news.google.com/rss/articles/<id>. The publisher URL is
     * fetched the same way the news.google.com page does it: read the signature and timestamp
     * from the article page, then ask the batchexecute endpoint for the real URL.
     */
    suspend fun decodeGoogleNews(link: String): String? {
        val id = link.substringAfter("/articles/", "").substringBefore('?').ifBlank { return null }
        val page = Http.get("https://news.google.com/articles/$id", maxBytes = 1_500_000)
        val sg = Regex("data-n-a-sg=\"([^\"]+)\"").find(page)?.groupValues?.get(1) ?: return null
        val ts = Regex("data-n-a-ts=\"([^\"]+)\"").find(page)?.groupValues?.get(1) ?: return null
        val inner = "[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1]," +
            "\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0],\"$id\",$ts,\"$sg\"]"
        val req = JsonArray(listOf(JsonArray(listOf(JsonArray(listOf(JsonPrimitive("Fbv4je"), JsonPrimitive(inner)))))))
        val resp = Http.postForm("https://news.google.com/_/DotsSplashUi/data/batchexecute", mapOf("f.req" to req.toString()))
        for (line in resp.lineSequence()) {
            if (!line.startsWith("[[")) continue
            val arr = runCatching { Json.parseToJsonElement(line).jsonArray }.getOrNull() ?: continue
            for (row in arr) {
                val r = row as? JsonArray ?: continue
                if (r.getOrNull(0)?.jsonPrimitive?.content != "wrb.fr") continue
                val payload = r.getOrNull(2)?.jsonPrimitive?.content ?: continue
                val data = runCatching { Json.parseToJsonElement(payload).jsonArray }.getOrNull() ?: continue
                val url = data.getOrNull(1)?.jsonPrimitive?.content
                if (url != null && url.startsWith("http")) return url
            }
        }
        return null
    }
}
