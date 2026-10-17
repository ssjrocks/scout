package com.ssjrocks.scout.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssjrocks.scout.BuildConfig
import com.ssjrocks.scout.data.Catalog
import com.ssjrocks.scout.data.Opml
import com.ssjrocks.scout.net.Place
import com.ssjrocks.scout.net.WeatherApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val lastRefresh by vm.repo.lastRefresh.collectAsStateWithLifecycle()
    val items by vm.repo.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var placeOpen by remember { mutableStateOf(false) }
    var redditOpen by remember { mutableStateOf(false) }

    fun msg(t: String) = vm.snack(t)

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.repo.update { it.copy(alertsEnabled = ok) }
        if (!ok) msg("Notifications are off for Scout in Android settings")
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.repo.exportBackup().toByteArray()) } }
                .onSuccess { msg("Settings exported") }.onFailure { msg("Export failed: ${it.message}") }
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openInputStream(uri)?.use { vm.repo.importBackup(it.readBytes().decodeToString()) } }
                .onSuccess { msg("Settings restored") }.onFailure { msg("That isn't a Scout backup (${it.message})") }
        }
    }
    val exportOpml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Opml.export(vm.repo.allSources()).toByteArray()) } }
                .onSuccess { msg("Sources exported as OPML") }.onFailure { msg("Export failed: ${it.message}") }
        }
    }
    val importOpml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
                val added = Opml.import(text, vm.repo.allSources())
                vm.repo.update { it.copy(customSources = it.customSources + added) }
                added.size
            }.onSuccess { n -> msg("Imported $n sources"); vm.refresh() }.onFailure { msg("Couldn't read that OPML file") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
            item { SectionTitle("Appearance") }
            item {
                ChoiceRow("Theme", listOf("system" to "Follow system", "light" to "Light", "dark" to "Dark", "black" to "Black (AMOLED)"), s.theme) { v ->
                    vm.repo.update { it.copy(theme = v) }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                item { SwitchRow("Wallpaper colours", "Material You colours instead of Google's", s.dynamicColor) { v -> vm.repo.update { it.copy(dynamicColor = v) } } }
            }
            item {
                SliderRow("Text size", s.textScale, 0.85f..1.35f, steps = 9, label = { "${(it * 100).toInt()}%" }) { v ->
                    vm.repo.update { it.copy(textScale = (v * 20).toInt() / 20f) }
                }
            }
            item { SwitchRow("Compact cards", "Small thumbnails instead of big pictures", s.compactCards) { v -> vm.repo.update { it.copy(compactCards = v) } } }
            item { SwitchRow("Swipe actions", "Swipe right to save, left to hide", s.swipeActions) { v -> vm.repo.update { it.copy(swipeActions = v) } } }

            item { SectionTitle("Reading") }
            item {
                ChoiceRow("Open stories in", listOf("tab" to "In-app browser (like Google)", "reader" to "Reader view", "browser" to "Browser app"), s.openIn) { v ->
                    vm.repo.update { it.copy(openIn = v) }
                }
            }
            item { SwitchRow("Open videos in the YouTube app", null, s.youtubeApp) { v -> vm.repo.update { it.copy(youtubeApp = v) } } }
            item { SwitchRow("Serif font in reader view", null, s.readerSerif) { v -> vm.repo.update { it.copy(readerSerif = v) } } }

            item { SectionTitle("Search bar") }
            item {
                ChoiceRow("Search with", listOf("google" to "Google", "ddg" to "DuckDuckGo", "startpage" to "Startpage", "bing" to "Bing"), s.searchEngine) { v ->
                    vm.repo.update { it.copy(searchEngine = v) }
                }
            }
            if (s.searchEngine == "google") {
                item { SwitchRow("Show results in the Google app", "Off: results open in the in-app browser", s.searchInGoogleApp) { v -> vm.repo.update { it.copy(searchInGoogleApp = v) } } }
            }
            item { SwitchRow("Remember recent searches", null, s.saveSearchHistory) { v -> vm.repo.update { it.copy(saveSearchHistory = v, searchHistory = if (v) it.searchHistory else emptyList()) } } }
            if (s.searchHistory.isNotEmpty()) {
                item { ClickRow("Clear recent searches", "${s.searchHistory.size} saved") { vm.repo.update { it.copy(searchHistory = emptyList()) } } }
            }

            item { SectionTitle("Weather") }
            item { SwitchRow("Show weather", null, s.showWeather) { v -> vm.repo.update { it.copy(showWeather = v) } } }
            item { ClickRow("Location", s.weatherPlace) { placeOpen = true } }
            item {
                SwitchRow("Fahrenheit", null, s.fahrenheit) { v ->
                    vm.repo.update { it.copy(fahrenheit = v) }
                    scope.launch { runCatching { vm.repo.refreshWeather(force = true) } }
                }
            }

            item { SectionTitle("Updates & data") }
            item {
                ChoiceRow(
                    "Refresh in the background",
                    listOf("0" to "Off (pull to refresh only)", "1" to "Every hour", "2" to "Every 2 hours", "3" to "Every 3 hours", "6" to "Every 6 hours", "12" to "Every 12 hours"),
                    s.refreshHours.toString(),
                ) { v -> vm.repo.update { it.copy(refreshHours = v.toInt()) } }
            }
            item { SwitchRow("Only refresh on Wi-Fi", null, s.refreshWifiOnly) { v -> vm.repo.update { it.copy(refreshWifiOnly = v) } } }
            item {
                ChoiceRow("Pictures", listOf("always" to "Always load", "wifi" to "Only on Wi-Fi", "never" to "Never (text only)"), s.images) { v ->
                    vm.repo.update { it.copy(images = v) }
                }
            }
            item {
                ChoiceRow("Keep stories for", listOf("3" to "3 days", "7" to "7 days", "14" to "14 days", "30" to "30 days"), s.keepDays.toString()) { v ->
                    vm.repo.update { it.copy(keepDays = v.toInt()) }
                }
            }
            item { ClickRow("Refresh now", "Last refresh: ${if (lastRefresh == 0L) "never" else shortAge(lastRefresh) + " ago"}") { vm.refresh(); msg("Refreshing…") } }

            item { SectionTitle("Reddit") }
            item {
                ClickRow(
                    "Reddit app ID",
                    if (s.redditClientId.isBlank()) "Not set: Reddit's public feeds allow only a few requests a minute, so subreddits update a few at a time"
                    else "Set: full-speed Reddit with scores and comment counts",
                ) { redditOpen = true }
            }

            item { SectionTitle("Alerts") }
            item {
                SwitchRow("Notify me about keywords", "After background refreshes, new stories with these words send a notification", s.alertsEnabled) { v ->
                    if (v && Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.repo.update { it.copy(alertsEnabled = v) }
                    }
                }
            }
            if (s.alertsEnabled) {
                item {
                    ClickRow("Send a test alert", "Shows the newest story that matches your words") {
                        val n = com.ssjrocks.scout.work.RefreshWorker.notifyMatches(ctx, vm.repo, test = true)
                        if (n == 0) msg("No stored story matches your alert words yet")
                    }
                }
                item {
                    WordListEditor(s.alertWords, "Add an alert word", onAdd = { w -> vm.repo.update { it.copy(alertWords = (it.alertWords + w).distinct()) } }, onRemove = { w ->
                        vm.repo.update { it.copy(alertWords = it.alertWords - w) }
                    })
                }
            }

            item { SectionTitle("Personalisation") }
            item {
                val top = s.learnedTopics.entries.sortedByDescending { it.value }.take(5)
                    .joinToString { (Catalog.topicById[it.key]?.name ?: it.key) + " ×%.1f".format(it.value) }
                ClickRow("What Scout has learned", top.ifBlank { "Nothing yet. Read, like and hide stories to teach it." }) {}
            }
            item { ClickRow("Reset learned interests", "Keeps your follows and blocks") { vm.repo.resetLearning(); msg("Learned interests reset") } }
            item { ClickRow("Show the welcome card again") { vm.repo.update { it.copy(showWelcome = true) }; vm.pop() } }

            item { SectionTitle("Backup") }
            item { ClickRow("Export settings", "Sources, blocks, interests and options as a JSON file") { exportBackup.launch("scout-settings.json") } }
            item { ClickRow("Restore settings") { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) } }
            item { ClickRow("Export sources as OPML", "Use them in any RSS reader") { exportOpml.launch("scout-sources.opml") } }
            item { ClickRow("Import sources from OPML") { importOpml.launch(arrayOf("text/x-opml", "text/xml", "application/xml", "*/*")) } }

            item { SectionTitle("About") }
            item {
                val srcs = vm.repo.allSources(s)
                ClickRow(
                    "Scout ${BuildConfig.VERSION_NAME}",
                    "${srcs.count { it.enabled }} sources on · ${items.size} stories stored · font: Google Sans Flex (SIL Open Font License)",
                ) {}
            }
        }
    }

    if (placeOpen) PlaceDialog(vm, onDismiss = { placeOpen = false })
    if (redditOpen) RedditDialog(vm, s.redditClientId, onDismiss = { redditOpen = false })
}

@Composable
private fun RedditDialog(vm: MainViewModel, current: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var id by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reddit app ID") },
        text = {
            Column {
                Text(
                    "Optional. Reddit's official API allows 100 requests a minute and gives scores and comment counts. " +
                        "To get an ID: sign in on reddit.com/prefs/apps, choose “create another app”, pick “installed app”, " +
                        "use any name and the redirect URI http://localhost, then copy the ID shown under the app's name.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { Links.openExternal(ctx, "https://www.reddit.com/prefs/apps") }) { Text("Open reddit.com/prefs/apps") }
                OutlinedTextField(value = id, onValueChange = { id = it.trim() }, label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.repo.update { it.copy(redditClientId = id) }
                onDismiss()
                vm.refresh()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlaceDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }

    fun choose(p: Place) {
        vm.repo.update { it.copy(weatherPlace = p.name, weatherLat = p.lat, weatherLon = p.lon) }
        scope.launch { runCatching { vm.repo.refreshWeather(force = true) } }
        onDismiss()
    }

    @SuppressLint("MissingPermission")
    fun useDevice() {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        if (loc == null) { status = "No recent location from the phone. Search for the place instead."; return }
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(ctx, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.locality
                }.getOrNull()
            } ?: "Current location"
            choose(Place(name, "", loc.latitude, loc.longitude))
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) useDevice() else status = "Location permission denied"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weather location") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { q ->
                        query = q
                        if (q.length >= 2) scope.launch { results = runCatching { WeatherApi.search(q) }.getOrDefault(emptyList()) }
                    },
                    label = { Text("Town or suburb") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) useDevice()
                    else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) {
                    Icon(Icons.Outlined.MyLocation, null)
                    Text("  Use my location")
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                results.forEach { p ->
                    Text(
                        p.name + if (p.area.isNotBlank()) " · ${p.area}" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { choose(p) }.padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
