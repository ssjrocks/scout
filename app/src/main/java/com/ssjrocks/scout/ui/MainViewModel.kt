package com.ssjrocks.scout.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssjrocks.scout.ScoutApp
import com.ssjrocks.scout.data.Item
import com.ssjrocks.scout.data.ItemState
import com.ssjrocks.scout.data.Settings
import com.ssjrocks.scout.data.Source
import com.ssjrocks.scout.net.AppUpdate
import com.ssjrocks.scout.net.Article
import com.ssjrocks.scout.net.Updater
import com.ssjrocks.scout.net.Discover
import com.ssjrocks.scout.net.Reader
import com.ssjrocks.scout.net.Suggest
import com.ssjrocks.scout.net.Trend
import com.ssjrocks.scout.rank.Entry
import com.ssjrocks.scout.rank.Mode
import com.ssjrocks.scout.rank.Ranker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

sealed interface Screen {
    data object Search : Screen
    data object Settings : Screen
    data class Reader(val itemId: String?, val url: String) : Screen
    data class Story(val itemId: String) : Screen
}

data class Snack(val message: String, val undo: (() -> Unit)? = null)

sealed interface UpdateUi {
    data object None : UpdateUi
    data class Available(val update: AppUpdate) : UpdateUi
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateUi
    data class Installing(val update: AppUpdate, val silent: Boolean) : UpdateUi
    data class Failed(val update: AppUpdate, val message: String) : UpdateUi
}

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Ready(val article: Article) : ReaderState
    data class Failed(val message: String) : ReaderState
}

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as ScoutApp).repo
    val settings: StateFlow<Settings> = repo.settings

    val screens = mutableStateListOf<Screen>()
    val snacks = MutableSharedFlow<Snack>(extraBufferCapacity = 4)
    val mode = MutableStateFlow<Mode>(Mode.ForYou)

    /** The story list the feed is ranked from. Background refreshes don't replace it while you read. */
    private val applied = MutableStateFlow<List<Item>>(emptyList())
    val newStories = MutableStateFlow(0)

    /** Settings that change what's shown. Learning, theme and search history don't re-rank. */
    private val rankKey = repo.settings.map {
        it.copy(
            learnedTopics = emptyMap(), learnedSources = emptyMap(), searchHistory = emptyList(), theme = "",
            dynamicColor = false, textScale = 1f, openIn = "", readerScale = 1f, readerSerif = false, showWelcome = false,
            alertWords = emptyList(), alertsEnabled = false, weatherPlace = "", weatherLat = 0.0, weatherLon = 0.0,
        )
    }.distinctUntilChanged()

    private val ranked: StateFlow<List<Entry>> = combine(applied, rankKey, mode) { items, _, m -> items to m }
        .map { (items, m) ->
            val s = repo.settings.value
            Ranker.rank(items, repo.sourceMap(s), repo.states.value, s, m)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Ranked order, with the latest story data (pictures arrive later) and read/saved state. */
    val feed: StateFlow<List<Entry>> = combine(ranked, repo.items, repo.states) { r, items, states ->
        val byId = items.associateBy { it.id }
        r.mapNotNull { e ->
            val st = states[e.id] ?: ItemState()
            if (st.hidden > 0) null else e.copy(item = byId[e.id] ?: e.item, state = st)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ready = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            repo.items.collect { list ->
                if (applied.value.isEmpty()) {
                    applied.value = list
                    if (list.isNotEmpty()) ready.value = true
                } else {
                    val known = applied.value.mapTo(HashSet()) { it.id }
                    newStories.value = list.count { it.id !in known && it.published > System.currentTimeMillis() - settings.value.maxAgeHours * 3_600_000L }
                }
            }
        }
        viewModelScope.launch {
            val stale = System.currentTimeMillis() - repo.lastRefresh.value > 15 * 60_000
            if (stale) refresh(userAsked = false)
            runCatching { repo.refreshWeather() }
            if (!ready.value) ready.value = true
        }
    }

    fun refresh(userAsked: Boolean = true) {
        viewModelScope.launch {
            repo.refresh()
            // Stories fetched while you're in the app don't need an alert later.
            repo.markAlertsChecked()
            if (userAsked || applied.value.isEmpty()) applyNew()
            ready.value = true
        }
    }

    fun applyNew() {
        applied.value = repo.items.value
        newStories.value = 0
    }

    fun setMode(m: Mode) {
        mode.value = m
        applied.value = repo.items.value
        newStories.value = 0
    }

    fun push(s: Screen) { screens.add(s) }
    fun pop() { if (screens.isNotEmpty()) screens.removeAt(screens.lastIndex) }

    fun snack(msg: String, undo: (() -> Unit)? = null) { snacks.tryEmit(Snack(msg, undo)) }

    // --- story actions with undo -----------------------------------------------------------

    fun hide(e: Entry) {
        repo.hide(e)
        snack("Story hidden") { repo.unhide(e.id) }
    }

    fun lessLike(e: Entry) {
        repo.lessLikeThis(e)
        snack("You'll see fewer stories like this") { repo.unhide(e.id) }
    }

    fun blockSource(e: Entry) {
        val before = repo.settings.value
        if (e.item.publisher != null) {
            repo.blockDomain(e.item.domain)
            snack("Stories from ${e.item.publisher} are blocked") { repo.update { before } }
        } else {
            repo.blockSource(e.source.id)
            snack("${e.source.name} is turned off") { repo.update { before } }
        }
    }

    fun blockDomain(domain: String) {
        val before = repo.settings.value
        repo.blockDomain(domain)
        snack("$domain is blocked") { repo.update { before } }
    }

    fun muteTopic(id: String, name: String) {
        val before = repo.settings.value
        repo.muteTopic(id)
        snack("Stories about $name are hidden") { repo.update { before } }
    }

    fun blockKind(kind: String, label: String) {
        val before = repo.settings.value
        repo.blockKind(kind)
        snack("$label are hidden") { repo.update { before } }
    }

    fun blockType(e: Entry) {
        val before = repo.settings.value
        repo.blockType(e.source.type)
        snack("All ${e.source.type.plural} are hidden") { repo.update { before } }
    }

    fun muteWord(w: String) {
        val before = repo.settings.value
        repo.muteWord(w)
        snack("Muted “$w”") { repo.update { before } }
    }

    fun moreFromSource(e: Entry, more: Boolean) {
        repo.editSource(e.source.id) { it.copy(weight = (it.weight * if (more) 1.4f else 0.6f).coerceIn(0.2f, 2.5f)) }
        snack(if (more) "You'll see more from ${e.source.name}" else "You'll see less from ${e.source.name}")
    }

    fun toggleSave(e: Entry) {
        val saved = repo.toggleSave(e)
        if (saved) {
            snack("Saved for later")
            viewModelScope.launch { cacheArticle(e.item) }
        } else snack("Removed from saved")
    }

    // --- reader --------------------------------------------------------------------------

    val reader = MutableStateFlow<ReaderState>(ReaderState.Loading)

    fun loadReader(itemId: String?, url: String) {
        reader.value = ReaderState.Loading
        viewModelScope.launch {
            val cached = itemId?.let { id -> withContext(Dispatchers.IO) { repo.db.readerCache(id) } }
            val art = cached?.let { runCatching { repo.json.decodeFromString(Article.serializer(), it) }.getOrNull() }
            if (art != null) { reader.value = ReaderState.Ready(art); return@launch }
            reader.value = try {
                val a = Reader.fetch(url)
                if (itemId != null) withContext(Dispatchers.IO) { repo.db.putReaderCache(itemId, repo.json.encodeToString(a)) }
                if (a.blocks.isEmpty()) ReaderState.Failed("Couldn't find the article text on this page.") else ReaderState.Ready(a)
            } catch (e: Exception) {
                ReaderState.Failed("Couldn't load the page (${e.message ?: e.javaClass.simpleName}).")
            }
        }
    }

    private suspend fun cacheArticle(item: Item) {
        if (repo.db.readerCache(item.id) != null) return
        runCatching {
            val a = Reader.fetch(item.link)
            withContext(Dispatchers.IO) { repo.db.putReaderCache(item.id, repo.json.encodeToString(a)) }
        }
    }

    // --- search --------------------------------------------------------------------------

    val query = MutableStateFlow("")
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val trends = MutableStateFlow<List<Trend>>(emptyList())

    init {
        viewModelScope.launch {
            query.debounce(180).distinctUntilChanged().collect { q ->
                suggestions.value = if (q.isBlank()) emptyList() else runCatching { Suggest.suggestions(q) }.getOrDefault(emptyList())
            }
        }
    }

    fun loadTrends() {
        if (trends.value.isNotEmpty()) return
        viewModelScope.launch { trends.value = runCatching { Suggest.trending() }.getOrDefault(emptyList()) }
    }

    fun localResults(q: String): List<Entry> {
        val words = q.lowercase().split(' ').filter { it.length > 1 }
        if (words.isEmpty()) return emptyList()
        val s = repo.settings.value
        val sources = repo.sourceMap(s)
        return repo.items.value.asSequence()
            .filter { item -> val t = (item.title + " " + item.summary).lowercase(); words.all { it in t } }
            .mapNotNull { item -> sources[item.sourceId]?.let { Entry(item, it, Ranker.topicsOf(item, it), 0.0, state = repo.state(item.id)) } }
            .sortedByDescending { it.item.published }
            .take(15).toList()
    }

    // --- app updates ---------------------------------------------------------------------

    val update = MutableStateFlow<UpdateUi>(UpdateUi.None)

    /** What "Check for updates now" found, shown under it in Settings (null = not checked yet). */
    val updateCheck = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("scout", android.content.Context.MODE_PRIVATE)
            prefs.getString("update_error", null)?.let { err ->
                prefs.edit().remove("update_error").apply()
                snack("The last update didn't install: $err")
            }
            val now = System.currentTimeMillis()
            if (settings.value.autoUpdate && now - prefs.getLong("update_checked_at", 0) > 3_600_000L) {
                prefs.edit().putLong("update_checked_at", now).apply()
                runCatching { Updater.check() }.getOrNull()?.let { u ->
                    if (u.versionCode != settings.value.skipUpdateCode) update.value = UpdateUi.Available(u)
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            Updater.results.collect { (status, msg) ->
                val cur = update.value as? UpdateUi.Installing ?: return@collect
                update.value = if (status == android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED) UpdateUi.Available(cur.update)
                else UpdateUi.Failed(cur.update, msg ?: "status $status")
            }
        }
    }

    /** From Settings: always asks GitHub, ignoring "Not now" and the check interval. */
    fun checkForUpdate(installIfFound: Boolean = false) {
        if (updateCheck.value == CHECKING) return
        updateCheck.value = CHECKING
        viewModelScope.launch {
            val at = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())
            try {
                val u = Updater.check()
                if (u == null) {
                    update.value = UpdateUi.None
                    updateCheck.value = "Up to date (checked $at)"
                    snack("Scout ${com.ssjrocks.scout.BuildConfig.VERSION_NAME} is the latest version")
                } else if (installIfFound) {
                    updateCheck.value = null
                    installUpdate(u)
                } else {
                    updateCheck.value = null
                    update.value = UpdateUi.Available(u)
                    snack("Scout ${u.versionName} is available")
                }
            } catch (e: Exception) {
                val why = e.message ?: e.javaClass.simpleName
                updateCheck.value = "Couldn't check at $at: $why"
                snack("Couldn't check for updates ($why)")
            }
        }
    }

    fun skipUpdate(u: AppUpdate) {
        repo.update { it.copy(skipUpdateCode = u.versionCode) }
        update.value = UpdateUi.None
    }

    fun installUpdate(u: AppUpdate) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                update.value = UpdateUi.Downloading(u, 0f)
                val file = Updater.download(ctx, u) { p -> update.value = UpdateUi.Downloading(u, p) }
                val silent = Updater.canInstallSilently(ctx)
                update.value = UpdateUi.Installing(u, silent)
                withContext(Dispatchers.IO) { Updater.install(ctx, file, u) }
            } catch (e: Exception) {
                update.value = UpdateUi.Failed(u, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    // --- sources -------------------------------------------------------------------------

    suspend fun discover(input: String, topic: String?): Discover.Found =
        Discover.resolve(input, topic ?: "tech").let { found ->
            if (topic == null) found.copy(source = found.source.copy(topic = Discover.guessTopic(found.source.name))) else found
        }

    fun addSource(src: Source) {
        repo.addSource(src)
        snack("Added ${src.name}")
    }

    companion object {
        const val CHECKING = "Checking GitHub…"
    }
}
