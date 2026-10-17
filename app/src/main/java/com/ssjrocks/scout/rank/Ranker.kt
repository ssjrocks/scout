package com.ssjrocks.scout.rank

import com.ssjrocks.scout.data.Catalog
import com.ssjrocks.scout.data.Item
import com.ssjrocks.scout.data.ItemState
import com.ssjrocks.scout.data.Kinds
import com.ssjrocks.scout.data.Settings
import com.ssjrocks.scout.data.Source
import com.ssjrocks.scout.data.SourceType
import com.ssjrocks.scout.net.FeedParser
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

sealed interface Mode {
    data object ForYou : Mode
    data object Latest : Mode
    data object Videos : Mode
    data object Unread : Mode
    data class Topic(val id: String) : Mode
    data class FromSource(val id: String) : Mode
}

data class Entry(
    val item: Item,
    val source: Source,
    val topics: List<String>,
    val score: Double,
    val related: List<Pair<Item, Source>> = emptyList(),
    val why: List<String> = emptyList(),
    val state: ItemState = ItemState(),
) {
    val id get() = item.id
}

private fun Entry.targetLink() = if (source.type == SourceType.REDDIT && item.extUrl != null) item.extUrl else item.link

object Ranker {

    private val topicRegex: Map<String, Regex> = Catalog.topics.filter { it.keywords.isNotEmpty() }.associate { t ->
        t.id to Regex("(?<![\\w])(" + t.keywords.joinToString("|") { Regex.escape(it) } + ")(?![\\w])", RegexOption.IGNORE_CASE)
    }

    private val clickbait = Regex(
        "(?i)(you won.?t believe|will (blow your mind|shock you)|here.?s (why|what|how)|this is why|the reason why|" +
            "what happened next|jaw.?dropping|goes viral|internet is (losing|freaking)|everyone is talking|" +
            "you need to (know|see)|must.?see|\\binsane\\b|unbelievable|mind.?blowing|gone wrong|" +
            "\\bshocking\\b|\\bslams\\b|\\bdestroys\\b|\\bepic fail\\b|^\\d+ (things|ways|reasons|signs|times|secrets)\\b|" +
            "😱|🤯|!!|\\?!|\\bwatch:|\\bdid you know\\b)",
    )
    private val shopping = Regex(
        "(?i)(\\bdeals?\\b|% off|save \\$|\\$\\d+ off|lowest price|price drop|prime (big deal )?day|black friday|" +
            "cyber monday|discount|on sale|sale price|\\bbargain|\\bcoupon|cheapest|best buy|\\bdeal alert)",
    )
    private val xMention = Regex("(?i)\\b(tweets?|tweeted|retweet(ed)?|twitter)\\b")
    private val xMentionCase = Regex("\\b((on|via|to|from) X\\b|X (post|thread|account|users?|feed)\\b)")

    private val stopwords = setOf(
        "the", "and", "for", "with", "that", "this", "from", "after", "over", "into", "about", "than", "have", "has",
        "are", "was", "were", "will", "its", "his", "her", "their", "they", "you", "your", "our", "new", "say", "says",
        "said", "how", "why", "what", "when", "who", "but", "not", "can", "could", "would", "should", "more", "most",
        "just", "now", "out", "off", "all", "one", "two", "first", "last", "get", "gets", "set", "amid", "being",
    )

    private val tokenCache = ConcurrentHashMap<String, Set<String>>()

    fun topicsOf(item: Item, source: Source): List<String> {
        val text = item.title + " " + item.summary.take(160)
        val tags = LinkedHashSet<String>()
        tags += source.topic
        for ((id, re) in topicRegex) if (re.containsMatchIn(text)) tags += id
        return tags.take(4)
    }

    private fun hostMatches(host: String, set: Set<String>) =
        host.isNotEmpty() && set.any { host == it || host.endsWith(".$it") }

    fun kindsOf(item: Item, source: Source): Set<String> {
        val k = HashSet<String>()
        val hosts = listOfNotNull(item.domain, item.extUrl?.let(FeedParser::hostOf), FeedParser.hostOf(item.link))
        if (hosts.any { hostMatches(it, Kinds.X_DOMAINS) }) k += Kinds.X
        if (xMention.containsMatchIn(item.title) || xMentionCase.containsMatchIn(item.title)) k += Kinds.X_MENTION
        if (hosts.any { hostMatches(it, Kinds.SOCIAL_DOMAINS) }) k += Kinds.SOCIAL
        if (source.type == SourceType.YOUTUBE || item.videoId != null || hosts.any { hostMatches(it, Kinds.VIDEO_DOMAINS) }) k += Kinds.VIDEO
        if (item.isShort) k += Kinds.SHORTS
        if (hosts.any { hostMatches(it, Kinds.PAYWALL_DOMAINS) }) k += Kinds.PAYWALL
        if (item.image == null) k += Kinds.NO_IMAGE
        if (clickbait.containsMatchIn(item.title)) k += Kinds.CLICKBAIT
        if (source.type != SourceType.DEALS && shopping.containsMatchIn(item.title)) k += Kinds.SHOPPING
        return k
    }

    /** Compiled form of the muted-words list: plain words match whole words, `re:` entries are regexes. */
    fun mutedMatcher(words: List<String>): Regex? {
        val parts = words.mapNotNull { w ->
            val t = w.trim()
            when {
                t.isEmpty() -> null
                t.startsWith("re:") -> runCatching { Regex(t.removePrefix("re:")); "(?:${t.removePrefix("re:")})" }.getOrNull()
                else -> "(?<![\\w])" + Regex.escape(t) + "(?![\\w])"
            }
        }
        if (parts.isEmpty()) return null
        return Regex(parts.joinToString("|"), RegexOption.IGNORE_CASE)
    }

    fun filterMatcher(spec: String): Regex? {
        val t = spec.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("re:")) return runCatching { Regex(t.removePrefix("re:"), RegexOption.IGNORE_CASE) }.getOrNull()
        val words = t.split('|', ',').map { it.trim() }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        return Regex(words.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    }

    class Filters(val s: Settings) {
        val muted = mutedMatcher(s.mutedWords)
        private val includes = HashMap<String, Regex?>()
        private val excludes = HashMap<String, Regex?>()
        fun include(src: Source) = includes.getOrPut(src.id) { filterMatcher(src.include) }
        fun exclude(src: Source) = excludes.getOrPut(src.id) { filterMatcher(src.exclude) }
    }

    /** Why an item is filtered out, or null if it may be shown. Ignores age and read state. */
    fun blockReason(item: Item, source: Source, topics: List<String>, kinds: Set<String>, s: Settings, f: Filters): String? {
        if (!source.enabled) return "source off"
        if (source.type.name in s.blockedTypes) return "type"
        val hosts = listOfNotNull(item.domain, item.extUrl?.let(FeedParser::hostOf))
        if (s.blockedDomains.isNotEmpty() && hosts.any { hostMatches(it, s.blockedDomains) }) return "site"
        if (kinds.any { it in s.blockedKinds }) return "kind"
        f.muted?.let { if (it.containsMatchIn(item.title) || it.containsMatchIn(item.summary) || (item.publisher != null && it.containsMatchIn(item.publisher))) return "muted" }
        if (topics.any { it in s.mutedTopics }) return "topic"
        f.include(source)?.let { if (!it.containsMatchIn(item.title)) return "include" }
        f.exclude(source)?.let { if (it.containsMatchIn(item.title)) return "exclude" }
        return null
    }

    private fun halfLifeHours(type: SourceType) = when (type) {
        SourceType.NEWS, SourceType.GNEWS -> 14.0
        SourceType.REDDIT -> 11.0
        SourceType.HN -> 12.0
        SourceType.YOUTUBE -> 24.0
        SourceType.RELEASES -> 48.0
        SourceType.STEAM -> 60.0
        SourceType.DEALS -> 18.0
    }

    fun rank(
        items: List<Item>,
        sources: Map<String, Source>,
        states: Map<String, ItemState>,
        s: Settings,
        mode: Mode,
        now: Long = System.currentTimeMillis(),
        limit: Int = 600,
    ): List<Entry> {
        val f = Filters(s)
        val horizon = now - s.maxAgeHours * 3_600_000L
        val candidates = ArrayList<Entry>()
        for (item in items) {
            val src = sources[item.sourceId] ?: continue
            val st = states[item.id] ?: ItemState()
            if (st.hidden > 0) continue
            if (item.published < horizon) continue
            if (item.expires != null && item.expires < now) continue
            val topics = topicsOf(item, src)
            val kinds = kindsOf(item, src)
            if (blockReason(item, src, topics, kinds, s, f) != null) continue
            when (mode) {
                is Mode.Topic -> if (mode.id !in topics) continue
                is Mode.FromSource -> if (src.id != mode.id) continue
                Mode.Videos -> if (Kinds.VIDEO !in kinds) continue
                Mode.Unread -> if (st.read > 0) continue
                Mode.ForYou -> if (s.hideRead && st.read > 0) continue
                Mode.Latest -> {}
            }
            val (score, why) = score(item, src, topics, st, s, now)
            candidates += Entry(item, src, topics, score, why = why, state = st)
        }
        val sorted = if (mode == Mode.Latest) candidates.sortedByDescending { it.item.published } else candidates.sortedByDescending { it.score }
        // The same article often arrives twice (a site's own feed and a Google News search): keep one.
        val seen = HashSet<String>()
        val unique = sorted.filter { e ->
            val title = e.item.title.lowercase().filter { it.isLetterOrDigit() }
            val link = FeedParser.normalizeUrl(e.targetLink())
            seen.add("t:$title") and seen.add("l:$link")
        }
        var list = unique.take(limit)
        if (s.clusterStories) list = cluster(list)
        if (s.mixSources && mode != Mode.Latest && mode !is Mode.FromSource) list = diversify(list)
        return list
    }

    private fun score(item: Item, src: Source, topics: List<String>, st: ItemState, s: Settings, now: Long): Pair<Double, List<String>> {
        val why = ArrayList<String>()
        val ageH = max(0.0, (now - item.published) / 3_600_000.0)
        val recency = 0.5.pow(ageH / halfLifeHours(src.type))
        val learnedSrc = if (s.learn) s.learnedSources[src.id] ?: 1f else 1f
        val srcW = src.weight * learnedSrc
        why += "From ${src.name}" + when {
            src.weight > 1.2f -> " (you asked for more from it)"
            src.weight < 0.8f -> " (shown less often)"
            else -> ""
        }
        // The best-liked topic sets the weight, but a topic you turned down still drags it down:
        // an AFL story from ABC News is "Australia" and "Sport", and shouldn't rank like world news.
        var topicW = 0.0
        var worst = 1.0
        var bestTopic: String? = null
        for (t in topics) {
            val w = ((s.topicWeights[t] ?: 1f) * (if (s.learn) s.learnedTopics[t] ?: 1f else 1f) *
                (if (t in s.followedTopics) 1f else 0.6f)).toDouble()
            if (w > topicW) { topicW = w; bestTopic = t }
            if (w < worst) worst = w
        }
        topicW *= worst
        bestTopic?.let { t ->
            val name = Catalog.topicById[t]?.name ?: t
            val learned = s.learnedTopics[t] ?: 1f
            why += when {
                (s.topicWeights[t] ?: 1f) > 1.2f -> "You asked for more about $name"
                learned > 1.15f -> "You often read about $name"
                t in s.followedTopics -> "You follow $name"
                else -> "About $name"
            }
        }
        val imageW = when {
            item.image != null && !item.imageSmall -> 1.15
            item.image != null -> 1.0
            else -> 0.85
        }
        val p = item.points ?: 0
        val engagement = when (src.type) {
            SourceType.HN -> 1 + ln(1.0 + p / 100.0) * 0.3
            SourceType.DEALS -> 1 + ln(1.0 + max(0, p) / 20.0) * 0.3
            SourceType.YOUTUBE -> 1 + ln(1.0 + p / 200_000.0) * 0.08
            else -> 1.0
        }
        if (src.type == SourceType.HN && p > 0) why += "$p points on Hacker News"
        if (src.type == SourceType.DEALS && p > 0) why += "+$p votes on OzBargain"
        val likedW = when (st.liked) { 1 -> 1.3; -1 -> 0.3; else -> 1.0 }
        val readW = if (st.read > 0) 0.35 else 1.0
        why += when {
            ageH < 1 -> "Fresh: under an hour old"
            ageH < 24 -> "Published ${ageH.toInt()}h ago"
            else -> "Published ${(ageH / 24).toInt()}d ago"
        }
        return recency * srcW * topicW * imageW * engagement * likedW * readW to why
    }

    fun tokens(item: Item): Set<String> = tokenCache.getOrPut(item.id) {
        item.title.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { (it.length >= 3 || it.any(Char::isDigit) && it.length >= 2) && it !in stopwords }
            .map { if (it.length > 4 && it.endsWith('s')) it.dropLast(1) else it }
            .toSet()
    }

    /** "Full coverage": folds near-duplicate headlines from different sources under the best-scoring one. */
    private fun cluster(list: List<Entry>): List<Entry> {
        val used = BooleanArray(list.size)
        val out = ArrayList<Entry>(list.size)
        val toks = list.map { tokens(it.item) }
        for (i in list.indices) {
            if (used[i]) continue
            val e = list[i]
            if (e.source.type == SourceType.DEALS || toks[i].size < 3) { out += e; continue }
            val related = ArrayList<Pair<Item, Source>>()
            for (j in i + 1 until list.size) {
                if (used[j] || related.size >= 6) continue
                val o = list[j]
                if (o.source.id == e.source.id || o.source.type == SourceType.DEALS) continue
                if (kotlin.math.abs(o.item.published - e.item.published) > 48 * 3_600_000L) continue
                val a = toks[i]; val b = toks[j]
                if (b.size < 3) continue
                val shared = a.count { it in b }
                val jaccard = shared.toDouble() / (a.size + b.size - shared)
                if (jaccard >= 0.5 || (shared >= 4 && jaccard >= 0.34)) {
                    used[j] = true
                    related += o.item to o.source
                }
            }
            out += if (related.isEmpty()) e else e.copy(related = related)
        }
        return out
    }

    /** Re-orders so the same source/type doesn't run several cards in a row. */
    private fun diversify(list: List<Entry>): List<Entry> {
        val pool = list.toMutableList()
        val out = ArrayList<Entry>(list.size)
        while (pool.isNotEmpty()) {
            var bestIdx = 0
            var best = Double.NEGATIVE_INFINITY
            val recent = out.takeLast(4)
            val lastTypes = out.takeLast(2).map { it.source.type }
            for (i in 0 until minOf(30, pool.size)) {
                val c = pool[i]
                var adj = c.score
                if (recent.any { it.source.id == c.source.id }) adj *= 0.5
                if (lastTypes.size == 2 && lastTypes.all { it == c.source.type } && c.source.type != SourceType.NEWS) adj *= 0.75
                if (adj > best) { best = adj; bestIdx = i }
            }
            out += pool.removeAt(bestIdx)
        }
        return out
    }
}
