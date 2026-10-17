package com.ssjrocks.scout.net

import android.util.Base64
import com.ssjrocks.scout.data.Item
import com.ssjrocks.scout.data.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Reddit's official API with an "installed app" client ID (no login): 100 requests a minute,
 * plus scores, comment counts and full-size preview images. Without a client ID Scout falls
 * back to the public RSS feeds, which Reddit limits to a few requests a minute per IP.
 */
object RedditApi {
    private const val UA = "android:com.ssjrocks.scout:1.0 (personal news reader)"

    fun subOf(src: Source): String? = Regex("/r/([A-Za-z0-9_]+)").find(src.url)?.groupValues?.get(1)

    /** Returns (token, expiry millis). */
    suspend fun token(clientId: String, deviceId: String): Pair<String, Long> {
        val basic = Base64.encodeToString("$clientId:".toByteArray(), Base64.NO_WRAP)
        val body = Http.postForm(
            "https://www.reddit.com/api/v1/access_token",
            mapOf("grant_type" to "https://oauth.reddit.com/grants/installed_client", "device_id" to deviceId),
            headers = mapOf("Authorization" to "Basic $basic", "User-Agent" to UA),
        )
        val j = Json.parseToJsonElement(body).jsonObject
        val token = j["access_token"]?.jsonPrimitive?.content
            ?: throw IllegalStateException(j["error"]?.jsonPrimitive?.content ?: "Reddit didn't return a token")
        val expires = j["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600
        return token to (System.currentTimeMillis() + (expires - 60) * 1000)
    }

    suspend fun top(sub: String, token: String): String =
        Http.get(
            "https://oauth.reddit.com/r/$sub/top?t=day&limit=20&raw_json=1",
            accept = "application/json",
            headers = mapOf("Authorization" to "Bearer $token", "User-Agent" to UA),
        )

    fun parse(src: Source, body: String, now: Long = System.currentTimeMillis()): List<Item> {
        val children = Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("children")?.jsonArray ?: return emptyList()
        return children.mapNotNull { c ->
            val d = c.jsonObject["data"]?.jsonObject ?: return@mapNotNull null
            fun str(k: String) = d[k]?.jsonPrimitive?.content?.takeIf { it != "null" && it.isNotBlank() }
            fun bool(k: String) = d[k]?.jsonPrimitive?.booleanOrNull == true
            if (bool("stickied") || bool("over_18")) return@mapNotNull null
            val permalink = str("permalink") ?: return@mapNotNull null
            val postUrl = "https://www.reddit.com$permalink"
            val outside = str("url_overridden_by_dest") ?: str("url")
            val outsideHost = outside?.let(FeedParser::hostOf).orEmpty()
            val isImage = outsideHost == "i.redd.it" || str("post_hint") == "image"
            val external = outside != null && !FeedParser.isRedditHost(outsideHost) && !bool("is_self")
            var image: String? = null
            var small = false
            if (isImage && outside != null) image = outside
            if (image == null) image = previewOf(d)
            if (image == null) {
                str("thumbnail")?.takeIf { it.startsWith("http") }?.let { image = it; small = true }
            }
            Item(
                id = FeedParser.itemId(postUrl),
                sourceId = src.id,
                title = FeedParser.clean(str("title") ?: return@mapNotNull null),
                link = postUrl,
                summary = str("selftext")?.let { FeedParser.truncate(FeedParser.clean(it), 300) }.orEmpty(),
                image = image,
                imageSmall = small,
                author = str("author")?.let { "u/$it" },
                published = ((d["created_utc"]?.jsonPrimitive?.doubleOrNull ?: (now / 1000.0)) * 1000).toLong().coerceAtMost(now),
                fetched = now,
                domain = if (external) outsideHost else "reddit.com",
                publisher = null,
                commentsUrl = postUrl,
                extUrl = if (external) outside else null,
                points = d["score"]?.jsonPrimitive?.intOrNull,
                comments = d["num_comments"]?.jsonPrimitive?.intOrNull,
                videoId = null,
                price = null,
                expires = null,
                enriched = image != null && !small || !external,
            )
        }
    }

    private fun previewOf(d: JsonObject): String? {
        val img = d["preview"]?.jsonObject?.get("images")?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val source = img["source"]?.jsonObject ?: return null
        val w = source["width"]?.jsonPrimitive?.intOrNull ?: 0
        // Prefer a ~1080px rendition over a huge original.
        val resolutions = img["resolutions"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        val pick = resolutions.lastOrNull { (it["width"]?.jsonPrimitive?.intOrNull ?: 0) <= 1080 && (it["width"]?.jsonPrimitive?.intOrNull ?: 0) >= 480 }
            ?: source.takeIf { w >= 320 }
        return pick?.get("url")?.jsonPrimitive?.content
    }
}
