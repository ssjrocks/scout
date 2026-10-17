package com.ssjrocks.scout.data

import kotlinx.serialization.Serializable

@Serializable
enum class SourceType(val label: String, val plural: String) {
    NEWS("News site", "News sites"),
    REDDIT("Reddit", "Reddit"),
    YOUTUBE("YouTube", "YouTube channels"),
    HN("Hacker News", "Hacker News"),
    DEALS("Deals", "OzBargain deals"),
    RELEASES("Releases", "App releases (GitHub)"),
    STEAM("Steam", "Steam game news"),
    GNEWS("Google News", "Google News searches"),
}

@Serializable
data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String,
    val topic: String,
    /** Domain used for the favicon and for "block this site". */
    val site: String = "",
    val enabled: Boolean = true,
    /** User weight: 0.3 = show much less, 1 = normal, 2 = show much more. */
    val weight: Float = 1f,
    /** Keep only items whose title matches: words separated by `|`, or `re:<regex>`. */
    val include: String = "",
    /** Drop items whose title matches (same syntax as [include]). */
    val exclude: String = "",
    val custom: Boolean = false,
)

/** User changes to a built-in source; null = keep the catalogue value. */
@Serializable
data class SourceOverride(
    val enabled: Boolean? = null,
    val weight: Float? = null,
    val include: String? = null,
    val exclude: String? = null,
)

data class Topic(val id: String, val name: String, val emoji: String, val keywords: List<String>)

data class Item(
    val id: String,
    val sourceId: String,
    val title: String,
    val link: String,
    val summary: String,
    val image: String?,
    /** Image is a small thumbnail (show the compact card). */
    val imageSmall: Boolean,
    val author: String?,
    val published: Long,
    val fetched: Long,
    /** Site the story lives on (publisher domain, or the outside link of a Reddit post). */
    val domain: String,
    /** Publisher name when it differs from the source (Google News). */
    val publisher: String?,
    val commentsUrl: String?,
    /** Outside link of a Reddit/HN post, when it isn't the story link itself. */
    val extUrl: String?,
    /** Score: Reddit/HN points, OzBargain votes, YouTube views. */
    val points: Int?,
    val comments: Int?,
    val videoId: String?,
    val price: String?,
    val expires: Long?,
    val enriched: Boolean,
) {
    val isShort: Boolean get() = videoId != null && link.contains("/shorts/")
}

data class ItemState(
    val read: Long = 0,
    val saved: Long = 0,
    val hidden: Long = 0,
    /** 1 = liked, -1 = "less like this". */
    val liked: Int = 0,
)

data class SourceStatus(
    val lastTry: Long = 0,
    val lastOk: Long = 0,
    val lastCount: Int = 0,
    val error: String? = null,
)

@Serializable
data class Settings(
    // Appearance
    val theme: String = "system", // system, light, dark, black
    val dynamicColor: Boolean = false,
    val textScale: Float = 1f,
    val compactCards: Boolean = false,
    val swipeActions: Boolean = true,
    // Reading
    val openIn: String = "tab", // tab, reader, browser
    val youtubeApp: Boolean = true,
    val readerSerif: Boolean = false,
    val readerScale: Float = 1f,
    // Data
    val images: String = "always", // always, wifi, never
    val refreshHours: Int = 1, // 0 = only when I pull to refresh
    val refreshWifiOnly: Boolean = false,
    val keepDays: Int = 7,
    // Feed
    val maxAgeHours: Int = 72,
    val hideRead: Boolean = false,
    val dimRead: Boolean = true,
    val clusterStories: Boolean = true,
    val mixSources: Boolean = true,
    val learn: Boolean = true,
    val showWelcome: Boolean = true,
    // Blocking
    val blockedKinds: Set<String> = setOf(Kinds.X),
    val blockedTypes: Set<String> = emptySet(),
    val blockedDomains: Set<String> = emptySet(),
    val mutedWords: List<String> = emptyList(),
    val mutedTopics: Set<String> = emptySet(),
    // Interests
    val followedTopics: Set<String> = Catalog.topics.map { it.id }.toSet() - Catalog.UNFOLLOWED_BY_DEFAULT,
    val topicWeights: Map<String, Float> = emptyMap(),
    val learnedTopics: Map<String, Float> = emptyMap(),
    val learnedSources: Map<String, Float> = emptyMap(),
    val sourceOverrides: Map<String, SourceOverride> = emptyMap(),
    val customSources: List<Source> = emptyList(),
    // Alerts
    val alertsEnabled: Boolean = false,
    val alertWords: List<String> = listOf("RTX 5080", "Claude Code", "Meshtastic", "Logan"),
    // App updates from GitHub releases
    val autoUpdate: Boolean = true,
    /** Version code the user said "Not now" to (not offered again or auto-installed). */
    val skipUpdateCode: Int = 0,
    // Reddit's "installed app" client ID, for the official API (optional)
    val redditClientId: String = "",
    // Search
    val searchEngine: String = "google", // google, ddg, startpage, bing
    val searchInGoogleApp: Boolean = true,
    val saveSearchHistory: Boolean = true,
    val searchHistory: List<String> = emptyList(),
    // Weather
    val showWeather: Boolean = true,
    val weatherPlace: String = "Park Ridge",
    val weatherLat: Double = -27.7009,
    val weatherLon: Double = 153.0352,
    val fahrenheit: Boolean = false,
)

/** Content kinds that can be blocked regardless of which source they came from. */
object Kinds {
    const val X = "x"
    const val X_MENTION = "x_mention"
    const val SOCIAL = "social"
    const val SHORTS = "shorts"
    const val VIDEO = "video"
    const val PAYWALL = "paywall"
    const val NO_IMAGE = "noimage"
    const val CLICKBAIT = "clickbait"
    const val SHOPPING = "shopping"

    val X_DOMAINS = setOf("x.com", "twitter.com", "nitter.net", "xcancel.com", "fxtwitter.com", "vxtwitter.com", "fixupx.com")
    val SOCIAL_DOMAINS = setOf(
        "facebook.com", "fb.watch", "instagram.com", "tiktok.com", "threads.net", "threads.com", "bsky.app",
        "mastodon.social", "truthsocial.com", "linkedin.com", "snapchat.com", "kick.com",
    )
    val VIDEO_DOMAINS = setOf("youtube.com", "youtu.be", "vimeo.com", "v.redd.it", "twitch.tv", "dailymotion.com", "rumble.com")
    val PAYWALL_DOMAINS = setOf(
        "couriermail.com.au", "theaustralian.com.au", "afr.com", "heraldsun.com.au", "dailytelegraph.com.au",
        "adelaidenow.com.au", "perthnow.com.au", "goldcoastbulletin.com.au", "brisbanetimes.com.au", "smh.com.au",
        "theage.com.au", "watoday.com.au", "nytimes.com", "wsj.com", "ft.com", "bloomberg.com", "economist.com",
        "washingtonpost.com", "theathletic.com", "thetimes.co.uk", "telegraph.co.uk", "newyorker.com", "wired.com",
        "businessinsider.com", "theinformation.com", "barrons.com", "seekingalpha.com", "theatlantic.com", "medium.com",
    )
}
