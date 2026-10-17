package com.ssjrocks.scout.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssjrocks.scout.data.Catalog
import com.ssjrocks.scout.data.Settings
import com.ssjrocks.scout.net.Weather
import com.ssjrocks.scout.net.WeatherApi
import com.ssjrocks.scout.rank.Entry
import com.ssjrocks.scout.rank.Mode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FeedScreen(vm: MainViewModel, padding: PaddingValues) {
    val feed by vm.feed.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val refreshing by vm.repo.refreshing.collectAsStateWithLifecycle()
    val progress by vm.repo.progress.collectAsStateWithLifecycle()
    val newCount by vm.newStories.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val weather by vm.repo.weather.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val lastRefresh by vm.repo.lastRefresh.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<Entry?>(null) }
    var weatherOpen by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val dark = isDark(s)

    // At the top of the feed, new stories just appear; further down they wait behind a button.
    LaunchedEffect(newCount, listState.firstVisibleItemIndex) {
        if (newCount > 0 && listState.firstVisibleItemIndex == 0 && !refreshing) vm.applyNew()
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { q ->
            vm.repo.addSearch(q)
            Links.search(ctx, q, s, dark)
        }
    }

    val actions = CardActions(
        open = { e ->
            vm.repo.opened(e)
            if (s.openIn == "reader" && com.ssjrocks.scout.data.Kinds.VIDEO !in com.ssjrocks.scout.rank.Ranker.kindsOf(e.item, e.source)) vm.push(Screen.Reader(e.id, e.targetUrl))
            else Links.open(ctx, e.targetUrl, s, dark)
        },
        openUrl = { url -> Links.open(ctx, url, s, dark) },
        like = { e -> vm.repo.toggleLike(e) },
        share = { e -> Links.share(ctx, e.item.title, e.item.link) },
        save = { e -> vm.toggleSave(e) },
        hide = { e -> vm.hide(e) },
        menu = { e -> menuFor = e },
    )

    Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        PullToRefreshBox(isRefreshing = refreshing && progress < 0.02f, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item("header") {
                    Header(
                        weather = if (s.showWeather) weather else null,
                        onWeather = { weatherOpen = true },
                        onSettings = { vm.push(Screen.Settings) },
                        onRefresh = { vm.refresh() },
                        onSearch = { vm.push(Screen.Search) },
                        onVoice = {
                            try {
                                voice.launch(
                                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search Google"),
                                )
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(ctx, "Voice input isn't available", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLens = { Links.lens(ctx) },
                    )
                }
                item("chips") { ModeChips(s, mode, vm::setMode) }
                if (s.showWelcome && mode == Mode.ForYou) {
                    item("welcome") {
                        WelcomeCard(
                            sources = vm.repo.allSources(s).count { it.enabled },
                            onDismiss = { vm.repo.update { it.copy(showWelcome = false) } },
                            onCustomise = { MainActivity.tab.value = 1; vm.repo.update { it.copy(showWelcome = false) } },
                        )
                    }
                }
                if (mode is Mode.FromSource) {
                    item("source-banner") {
                        val src = vm.repo.sourceMap(s)[(mode as Mode.FromSource).id]
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Only ${src?.name ?: "one source"}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.setMode(Mode.ForYou) }) { Text("Show all") }
                        }
                    }
                }
                if (feed.isEmpty()) {
                    item("empty") {
                        if (!ready || refreshing) LoadingCards()
                        else EmptyState(
                            "📭", "Nothing to show",
                            if (mode == Mode.ForYou) "Pull down to refresh, or turn on more sources in Following." else "No stories match this view right now.",
                        ) { OutlinedButton(onClick = { vm.refresh() }) { Text("Refresh") } }
                    }
                }
                items(feed, key = { it.id }, contentType = { if (it.item.image != null && !it.item.imageSmall && !s.compactCards) 1 else 2 }) { e ->
                    StoryCard(e, s, actions)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (feed.isNotEmpty()) {
                    item("footer") {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✓", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Text("You're all caught up", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${feed.size} stories · updated ${if (lastRefresh == 0L) "never" else shortAge(lastRefresh) + " ago"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { vm.refresh(); scope.launch { listState.animateScrollToItem(0) } }) { Text("Check for new stories") }
                        }
                    }
                }
            }
        }
        if (refreshing && progress >= 0.02f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
        AnimatedVisibility(
            visible = newCount > 0 && listState.firstVisibleItemIndex > 0,
            enter = slideInVertically() + fadeIn(), exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, shadowElevation = 6.dp,
                modifier = Modifier.clickable { vm.applyNew(); scope.launch { listState.scrollToItem(0) } },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ArrowUpward, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("$newCount new stories", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    menuFor?.let { e -> StorySheet(vm, e, s, onDismiss = { menuFor = null }) }
    if (weatherOpen && weather != null) {
        WeatherSheet(weather!!, s, onDismiss = { weatherOpen = false }, onChangePlace = { weatherOpen = false; vm.push(Screen.Settings) })
    }
}

@Composable
private fun Header(
    weather: Weather?, onWeather: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit,
    onSearch: () -> Unit, onVoice: () -> Unit, onLens: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (weather != null) {
                val (emoji, _) = WeatherApi.describe(weather.code, weather.isDay)
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onWeather).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("${weather.temp.roundToInt()}°", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Refresh") }
            IconButton(onClick = onSettings) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Settings, "Settings", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp)) }
            }
        }
        GoogleWordmark(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 18.dp))
        SearchPill(onSearch, onVoice, onLens)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun GoogleWordmark(modifier: Modifier = Modifier) {
    val blue = Color(0xFF4285F4); val red = Color(0xFFEA4335); val yellow = Color(0xFFFBBC05); val green = Color(0xFF34A853)
    val text = buildAnnotatedString {
        listOf("G" to blue, "o" to red, "o" to yellow, "g" to blue, "l" to green, "e" to red).forEach { (c, col) ->
            withStyle(SpanStyle(color = col)) { append(c) }
        }
    }
    Text(text, fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 46.sp, letterSpacing = (-1.5).sp, modifier = modifier)
}

@Composable
fun SearchPill(onSearch: () -> Unit, onVoice: () -> Unit, onLens: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).clickable(onClick = onSearch),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            GoogleG(Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text("Search", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = onVoice) { Icon(Icons.Outlined.Mic, "Voice search", tint = MaterialTheme.colorScheme.onSurface) }
            IconButton(onClick = onLens) { Icon(Icons.Outlined.CameraAlt, "Google Lens", tint = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun ModeChips(s: Settings, mode: Mode, onMode: (Mode) -> Unit) {
    val fixed = listOf<Pair<Mode, String>>(Mode.ForYou to "For you", Mode.Latest to "Latest", Mode.Videos to "▶ Videos", Mode.Unread to "Unread")
    val topics = Catalog.topics.filter { it.id in s.followedTopics && it.id !in s.mutedTopics }.map { Mode.Topic(it.id) to "${it.emoji} ${it.name}" }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        items(fixed + topics, key = { it.second }) { (m, label) ->
            FilterChip(
                selected = mode == m, onClick = { onMode(if (mode == m && m != Mode.ForYou) Mode.ForYou else m) },
                label = { Text(label) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun WelcomeCard(sources: Int, onDismiss: () -> Unit, onCustomise: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Your feed, your rules", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(
                "Scout follows $sources sources picked from what you browse: PC hardware, local AI, Linux and self-hosting, " +
                    "ESP32 and 3D printing, games, Game of Thrones, science, privacy, comedy and Logan/Brisbane news. " +
                    "Posts from X/Twitter are already blocked. Long-press or use ⋮ on any story to block its source, " +
                    "site, topic or type.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Got it") }
                Button(onClick = onCustomise) { Text("Customise") }
            }
        }
    }
}

@Composable
private fun LoadingCards() {
    Column {
        repeat(3) {
            Column(Modifier.padding(16.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(0.9f).height(18.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainer))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.6f).height(18.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainer))
            }
        }
        Text(
            "Fetching your sources…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
