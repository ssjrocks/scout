package com.ssjrocks.scout.data

import android.content.Context
import android.util.Log
import com.ssjrocks.scout.net.Enrich
import com.ssjrocks.scout.net.FeedParser
import com.ssjrocks.scout.net.Http
import com.ssjrocks.scout.net.HttpException
import com.ssjrocks.scout.net.RedditApi
import com.ssjrocks.scout.net.Weather
import com.ssjrocks.scout.net.WeatherApi
import com.ssjrocks.scout.rank.Entry
import com.ssjrocks.scout.rank.Mode
import com.ssjrocks.scout.rank.Ranker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WidgetStory(val id: String, val title: String, val source: String, val link: String, val published: Long, val image: String?)

@Serializable
data class Backup(val version: Int = 1, val settings: Settings)

class Repo(private val app: Context) {
    val db = Db(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = app.getSharedPreferences("scout", Context.MODE_PRIVATE)
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings

    val items = MutableStateFlow<List<Item>>(emptyList())
    val states = MutableStateFlow<Map<String, ItemState>>(emptyMap())
    val statuses = MutableStateFlow<Map<String, SourceStatus>>(emptyMap())
    val refreshing = MutableStateFlow(false)
    val progress = MutableStateFlow(0f)
    val lastRefresh = MutableStateFlow(prefs.getLong("last_refresh", 0))
    val weather = MutableStateFlow(prefs.getString("weather", null)?.let { runCatching { json.decodeFromString<Weather>(it) }.getOrNull() })

    private val refreshLock = Mutex()

    init {
        scope.launch { reload() }
    }

    // --- settings -------------------------------------------------------------------------

    private fun loadSettings(): Settings =
        prefs.getString("settings", null)?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()

    fun update(f: (Settings) -> Settings) {
        val next = f(_settings.value)
        _settings.value = next
        prefs.edit().putString("settings", json.encodeToString(next)).apply()
    }

    fun exportBackup(): String = json.encodeToString(Backup(settings = _settings.value))

    fun importBackup(text: String) {
        val b = json.decodeFromString<Backup>(text)
        update { b.settings }
    }

    // --- sources --------------------------------------------------------------------------

    fun allSources(s: Settings = _settings.value): List<Source> =
        Catalog.sources.map { src ->
            val o = s.sourceOverrides[src.id] ?: return@map src
            src.copy(
                enabled = o.enabled ?: src.enabled, weight = o.weight ?: src.weight,
                include = o.include ?: src.include, exclude = o.exclude ?: src.exclude,
            )
        } + s.customSources

    fun sourceMap(s: Settings = _settings.value) = allSources(s).associateBy { it.id }

    fun editSource(id: String, f: (Source) -> Source) {
        update { s ->
            val custom = s.customSources.indexOfFirst { it.id == id }
            if (custom >= 0) {
                s.copy(customSources = s.customSources.toMutableList().also { it[custom] = f(it[custom]) })
            } else {
                val cur = sourceMap(s)[id] ?: return@update s
                val n = f(cur)
                val base = Catalog.sourceById[id] ?: return@update s
                val o = SourceOverride(
                    enabled = n.enabled.takeIf { it != base.enabled }, weight = n.weight.takeIf { it != base.weight },
                    include = n.include.takeIf { it != base.include }, exclude = n.exclude.takeIf { it != base.exclude },
                )
                s.copy(sourceOverrides = if (o == SourceOverride()) s.sourceOverrides - id else s.sourceOverrides + (id to o))
            }
        }
    }

    fun addSource(src: Source) {
        update { s -> s.copy(customSources = s.customSources.filter { it.url != src.url } + src) }
        scope.launch { fetchSource(src); reload(); enrich(20); reload() }
    }

    fun removeSource(id: String) {
        update { s -> s.copy(customSources = s.customSources.filter { it.id != id }) }
        scope.launch { withContext(Dispatchers.IO) { db.deleteSource(id) }; reload() }
    }

    // --- loading & refreshing -------------------------------------------------------------

    suspend fun reload() = withContext(Dispatchers.IO) {
        val s = _settings.value
        val since = System.currentTimeMillis() - s.keepDays * 86_400_000L
        items.value = db.items(since)
        states.value = db.states()
        statuses.value = db.statuses()
    }

    private var redditJob: Job? = null

    /**
     * Fetches every enabled source. Returns the ids of stories that are new. Reddit is fetched
     * afterwards in the background (it allows only ~10 anonymous requests a minute) unless
     * [waitForReddit] is set, as the background worker does.
     */
    suspend fun refresh(waitForReddit: Boolean = false): List<String> {
        if (!refreshLock.tryLock()) return emptyList()
        try {
            refreshing.value = true
            progress.value = 0f
            val s = _settings.value
            val sources = allSources(s).filter { it.enabled && it.type.name !in s.blockedTypes }
            val (reddit, rest) = sources.partition { it.type == SourceType.REDDIT }
            val total = rest.size.coerceAtLeast(1)
            var done = 0
            val tick = { synchronized(this) { done++; progress.value = done / total.toFloat() } }
            transientFailures.clear()
            val fresh = coroutineScope {
                val sem = Semaphore(6)
                rest.map { src -> async { sem.withPermit { (fetchSource(src) ?: emptyList()).also { tick() } } } }.awaitAll().flatten()
            }.toMutableList()
            // YouTube's feed server has bad spells lasting tens of seconds: try those again once.
            val again = rest.filter { it.id in transientFailures }
            if (again.isNotEmpty()) {
                delay(5000)
                for (src in again) fresh += fetchSource(src) ?: emptyList()
            }
            withContext(Dispatchers.IO) { db.prune(System.currentTimeMillis() - s.keepDays * 86_400_000L) }
            reload()
            enrich(60)
            reload()
            val now = System.currentTimeMillis()
            lastRefresh.value = now
            prefs.edit().putLong("last_refresh", now).apply()
            saveWidgetStories()
            runCatching { refreshWeather() }
            val redditFresh = if (reddit.isEmpty() || redditJob?.isActive == true) {
                emptyList()
            } else if (waitForReddit) {
                fetchReddit(reddit)
            } else {
                redditJob = scope.launch { fetchReddit(reddit) }
                emptyList()
            }
            return fresh + redditFresh
        } finally {
            refreshing.value = false
            refreshLock.unlock()
        }
    }

    private var redditToken: Pair<String, Long>? = null

    private fun deviceId(): String = prefs.getString("device_id", null) ?: java.util.UUID.randomUUID().toString().also {
        prefs.edit().putString("device_id", it).apply()
    }

    /** Bearer token for Reddit's API when the user has set a client ID, else null (use RSS). */
    private suspend fun redditBearer(): String? {
        val id = _settings.value.redditClientId.trim()
        if (id.isEmpty()) return null
        redditToken?.let { (t, exp) -> if (System.currentTimeMillis() < exp) return t }
        return try {
            RedditApi.token(id, deviceId()).also { redditToken = it }.first
        } catch (e: Exception) {
            Log.w("Scout", "reddit token failed: $e")
            null
        }
    }

    /**
     * Without an API client ID Reddit allows only a few anonymous feed requests a minute, so each
     * refresh takes the subreddits that were updated longest ago, paces itself by Reddit's reset
     * header, and stops at the first 429. With a client ID every subreddit is fetched each time.
     */
    private suspend fun fetchReddit(subs: List<Source>): List<String> {
        val bearer = redditBearer()
        val order = subs.sortedBy { statuses.value[it.id]?.lastOk ?: 0L }
        val batch = if (bearer != null) order else order.take(5)
        val fresh = ArrayList<String>()
        var wait = 0L
        for ((i, src) in batch.withIndex()) {
            if (i > 0) delay(if (bearer != null) 700L else maxOf(7000L, wait))
            val now = System.currentTimeMillis()
            try {
                val items = if (bearer != null) {
                    val sub = RedditApi.subOf(src) ?: continue
                    withContext(Dispatchers.Default) { RedditApi.parse(src, RedditApi.top(sub, bearer), now) }
                } else {
                    withContext(Dispatchers.Default) { FeedParser.parse(src, Http.get(src.url), now).items }
                }
                fresh += withContext(Dispatchers.IO) { db.upsertItems(items) }
                withContext(Dispatchers.IO) { db.putStatus(src.id, SourceStatus(now, now, items.size, null)) }
            } catch (e: Exception) {
                Log.w("Scout", "reddit ${src.id} failed: $e")
                val limited = e is HttpException && e.code == 429
                val msg = if (limited) "Waiting for Reddit's rate limit" else (if (e is HttpException) "HTTP ${e.code}" else e.javaClass.simpleName)
                withContext(Dispatchers.IO) { db.putStatus(src.id, (statuses.value[src.id] ?: SourceStatus()).copy(lastTry = now, error = msg)) }
                if (limited) break
                if (e is HttpException && e.code == 401) redditToken = null
                wait = ((e as? HttpException)?.resetSeconds ?: 0) * 1000L + 1000L
            }
        }
        reload()
        enrich(30)
        reload()
        saveWidgetStories()
        return fresh
    }

    /** Sources that failed with a passing error during the current refresh (retried once at the end). */
    private val transientFailures = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Returns new story ids, or null when the source answered 429 even after waiting. */
    private suspend fun fetchSource(src: Source, retry429: Boolean = false): List<String>? {
        val now = System.currentTimeMillis()
        val prev = statuses.value[src.id] ?: SourceStatus()
        val accept = "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5"
        return try {
            var xml: String? = null
            var attempt = 0
            while (xml == null) {
                try {
                    xml = Http.get(src.url, accept = accept)
                } catch (e: HttpException) {
                    attempt++
                    // YouTube's feed endpoint answers about half of all requests with a random
                    // 404/500 (measured Oct 2026), so retry it quickly several times. Other sites'
                    // 5xx are usually brief.
                    val youtube = src.type == SourceType.YOUTUBE && (e.code == 404 || e.code >= 500)
                    when {
                        e.code == 429 && retry429 && attempt == 1 -> delay(60_000)
                        youtube && attempt < 6 -> delay(1000L * attempt)
                        e.code >= 500 && attempt < 3 -> delay(1500L * attempt)
                        else -> throw e
                    }
                }
            }
            val parsed = withContext(Dispatchers.Default) { FeedParser.parse(src, xml!!, now) }
            val added = withContext(Dispatchers.IO) { db.upsertItems(parsed.items) }
            withContext(Dispatchers.IO) { db.putStatus(src.id, SourceStatus(now, now, parsed.items.size, null)) }
            added
        } catch (e: Exception) {
            Log.w("Scout", "fetch ${src.id} failed: $e")
            if ((e is HttpException && (e.code >= 500 || src.type == SourceType.YOUTUBE)) || e is java.net.SocketTimeoutException) {
                transientFailures += src.id
            }
            val msg = when (e) {
                is HttpException -> if (e.code == 429) "Rate limited, will retry" else "HTTP ${e.code}"
                is java.net.UnknownHostException -> "Offline"
                is java.net.SocketTimeoutException -> "Timed out"
                else -> e.javaClass.simpleName
            }
            withContext(Dispatchers.IO) { db.putStatus(src.id, prev.copy(lastTry = now, error = msg)) }
            if (e is HttpException && e.code == 429) null else emptyList()
        }
    }

    /** Pictures for image-less stories, real links for Google News, and Shorts detection. */
    private suspend fun enrich(limit: Int) = coroutineScope {
        val todo = withContext(Dispatchers.IO) { db.itemsNeedingEnrichment(limit) }
        val sources = sourceMap()
        val sem = Semaphore(5)
        todo.map { item ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val src = sources[item.sourceId]
                    try {
                        when (src?.type) {
                            SourceType.GNEWS -> {
                                val real = Enrich.decodeGoogleNews(item.link)
                                val img = real?.let { runCatching { Enrich.ogImage(it) }.getOrNull() }
                                db.setEnriched(item.id, img, real, real?.let(FeedParser::hostOf))
                            }
                            SourceType.REDDIT -> {
                                val img = item.extUrl?.let { runCatching { Enrich.ogImage(it) }.getOrNull() }
                                db.setEnriched(item.id, img, null, null)
                            }
                            SourceType.HN -> {
                                val img = runCatching { Enrich.ogImage(item.link) }.getOrNull()
                                db.setEnriched(item.id, img, null, null)
                            }
                            null -> db.setEnriched(item.id, null, null, null)
                            else -> db.setEnriched(item.id, runCatching { Enrich.ogImage(item.link) }.getOrNull(), null, null)
                        }
                    } catch (e: Exception) {
                        db.setEnriched(item.id, null, null, null)
                    }
                }
            }
        }.awaitAll()
    }

    suspend fun refreshWeather(force: Boolean = false) {
        val s = _settings.value
        if (!s.showWeather) return
        val cur = weather.value
        if (!force && cur != null && cur.place == s.weatherPlace && System.currentTimeMillis() - cur.fetched < 20 * 60_000) return
        val w = WeatherApi.fetch(s.weatherPlace, s.weatherLat, s.weatherLon, s.fahrenheit)
        weather.value = w
        prefs.edit().putString("weather", json.encodeToString(w)).apply()
    }

    // --- alerts ---------------------------------------------------------------------------

    fun alertsCheckedAt(): Long = prefs.getLong("alerts_checked", System.currentTimeMillis() - 2 * 3_600_000L)
    fun markAlertsChecked() = prefs.edit().putLong("alerts_checked", System.currentTimeMillis()).apply()

    // --- widget ---------------------------------------------------------------------------

    fun saveWidgetStories() {
        val s = _settings.value
        val top = Ranker.rank(items.value, sourceMap(s), states.value, s, Mode.ForYou, limit = 200)
            .filter { it.state.read == 0L }
            .take(10)
            .map { WidgetStory(it.item.id, it.item.title, it.item.publisher ?: it.source.name, it.item.link, it.item.published, it.item.image) }
        prefs.edit().putString("widget", json.encodeToString(top)).apply()
        com.ssjrocks.scout.widget.ScoutWidget.requestUpdate(app)
    }

    fun widgetStories(): List<WidgetStory> =
        prefs.getString("widget", null)?.let { runCatching { json.decodeFromString<List<WidgetStory>>(it) }.getOrNull() }.orEmpty()

    // --- per-story actions ----------------------------------------------------------------

    fun state(id: String) = states.value[id] ?: ItemState()

    fun setState(id: String, f: (ItemState) -> ItemState) {
        val n = f(state(id))
        states.value = states.value + (id to n)
        scope.launch(Dispatchers.IO) { db.putState(id, n) }
    }

    private fun learn(e: Entry, topicDelta: Float, sourceDelta: Float) {
        if (!_settings.value.learn) return
        update { s ->
            val lt = s.learnedTopics.toMutableMap()
            for (t in e.topics) lt[t] = ((lt[t] ?: 1f) + topicDelta).coerceIn(0.3f, 2.5f)
            val ls = s.learnedSources.toMutableMap()
            ls[e.source.id] = ((ls[e.source.id] ?: 1f) + sourceDelta).coerceIn(0.3f, 2.5f)
            s.copy(learnedTopics = lt, learnedSources = ls)
        }
    }

    fun opened(e: Entry) {
        if (state(e.id).read == 0L) learn(e, 0.04f, 0.03f)
        setState(e.id) { it.copy(read = System.currentTimeMillis()) }
    }

    fun markUnread(id: String) = setState(id) { it.copy(read = 0) }

    fun toggleSave(e: Entry): Boolean {
        val saving = state(e.id).saved == 0L
        if (saving) learn(e, 0.08f, 0.05f)
        setState(e.id) { it.copy(saved = if (saving) System.currentTimeMillis() else 0) }
        return saving
    }

    fun toggleLike(e: Entry) {
        val liking = state(e.id).liked != 1
        learn(e, if (liking) 0.15f else -0.15f, if (liking) 0.1f else -0.1f)
        setState(e.id) { it.copy(liked = if (liking) 1 else 0) }
    }

    fun lessLikeThis(e: Entry) {
        learn(e, -0.2f, -0.15f)
        setState(e.id) { it.copy(liked = -1, hidden = System.currentTimeMillis()) }
    }

    fun hide(e: Entry) {
        learn(e, -0.03f, -0.03f)
        setState(e.id) { it.copy(hidden = System.currentTimeMillis()) }
    }

    fun unhide(id: String) = setState(id) { it.copy(hidden = 0, liked = if (it.liked == -1) 0 else it.liked) }

    fun clearHidden() {
        states.value = states.value.mapValues { it.value.copy(hidden = 0) }
        scope.launch(Dispatchers.IO) { db.clearHidden() }
    }

    fun clearHistory() {
        states.value = states.value.mapValues { it.value.copy(read = 0) }
        scope.launch(Dispatchers.IO) { db.clearHistory() }
    }

    fun resetLearning() = update { it.copy(learnedTopics = emptyMap(), learnedSources = emptyMap()) }

    fun blockSource(id: String) = editSource(id) { it.copy(enabled = false) }
    fun blockDomain(domain: String) = update { it.copy(blockedDomains = it.blockedDomains + domain) }
    fun muteTopic(id: String) = update { it.copy(mutedTopics = it.mutedTopics + id) }
    fun muteWord(w: String) = update { it.copy(mutedWords = (it.mutedWords + w.trim()).distinct()) }
    fun blockKind(kind: String) = update { it.copy(blockedKinds = it.blockedKinds + kind) }
    fun blockType(type: SourceType) = update { it.copy(blockedTypes = it.blockedTypes + type.name) }

    fun addSearch(q: String) {
        if (!_settings.value.saveSearchHistory || q.isBlank()) return
        update { s -> s.copy(searchHistory = (listOf(q.trim()) + s.searchHistory.filter { !it.equals(q.trim(), true) }).take(20)) }
    }
}
