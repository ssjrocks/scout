package com.ssjrocks.scout.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLEncoder

data class Trend(val query: String, val traffic: String?, val newsTitle: String?, val newsUrl: String?, val picture: String?)

/** Search-box helpers: Google's autocomplete and Google Trends' daily trending searches. */
object Suggest {

    suspend fun suggestions(q: String): List<String> {
        if (q.isBlank()) return emptyList()
        val url = "https://suggestqueries.google.com/complete/search?client=firefox&hl=en-AU&q=" + URLEncoder.encode(q, "UTF-8")
        val arr = Json.parseToJsonElement(Http.get(url)).jsonArray
        return arr.getOrNull(1)?.jsonArray?.map { it.jsonPrimitive.content }?.take(8).orEmpty()
    }

    suspend fun trending(geo: String = "AU"): List<Trend> {
        val xml = Http.get("https://trends.google.com/trending/rss?geo=$geo")
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.getElementsByTag("item").map { it ->
            val news = it.getElementsByTag("ht:news_item").firstOrNull()
            Trend(
                query = it.getElementsByTag("title").first()?.text().orEmpty(),
                traffic = it.getElementsByTag("ht:approx_traffic").first()?.text(),
                newsTitle = news?.getElementsByTag("ht:news_item_title")?.first()?.text()?.let { t -> Jsoup.parse(t).text() },
                newsUrl = news?.getElementsByTag("ht:news_item_url")?.first()?.text(),
                picture = it.getElementsByTag("ht:picture").first()?.text(),
            )
        }.filter { it.query.isNotBlank() }
    }
}
