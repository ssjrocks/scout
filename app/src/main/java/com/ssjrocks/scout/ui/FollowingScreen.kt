package com.ssjrocks.scout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssjrocks.scout.data.Catalog
import com.ssjrocks.scout.data.Source
import com.ssjrocks.scout.data.SourceStatus
import com.ssjrocks.scout.data.Topic
import com.ssjrocks.scout.net.Discover
import com.ssjrocks.scout.rank.Mode
import kotlinx.coroutines.launch

@Composable
fun FollowingScreen(vm: MainViewModel, padding: PaddingValues) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val statuses by vm.repo.statuses.collectAsStateWithLifecycle()
    val pending by MainActivity.pendingAdd.collectAsStateWithLifecycle()
    val sources = remember(s) { vm.repo.allSources(s) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Source?>(null) }

    LaunchedEffect(pending) { if (pending != null) adding = true }

    Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp)) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                    Text("Following", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${sources.count { it.enabled }} of ${sources.size} sources on · ${s.followedTopics.size} topics followed. " +
                            "Tap a topic to see its sources. Picks are based on your browsing.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Catalog.topics.forEach { topic ->
                val inTopic = sources.filter { it.topic == topic.id }
                item(key = "t_${topic.id}") {
                    TopicRow(
                        topic, inTopic, followed = topic.id in s.followedTopics, muted = topic.id in s.mutedTopics,
                        weight = s.topicWeights[topic.id] ?: 1f, expanded = expanded[topic.id] == true,
                        onExpand = { expanded[topic.id] = expanded[topic.id] != true },
                        onFollow = { on ->
                            vm.repo.update { st -> st.copy(followedTopics = if (on) st.followedTopics + topic.id else st.followedTopics - topic.id, mutedTopics = if (on) st.mutedTopics - topic.id else st.mutedTopics) }
                        },
                        onWeight = { w -> vm.repo.update { st -> st.copy(topicWeights = if (w == 1f) st.topicWeights - topic.id else st.topicWeights + (topic.id to w)) } },
                    )
                }
                if (expanded[topic.id] == true) {
                    items(inTopic, key = { "s_${it.id}" }) { src ->
                        SourceRow(src, statuses[src.id], onToggle = { on -> vm.repo.editSource(src.id) { it.copy(enabled = on) } }, onClick = { editing = src })
                    }
                }
                item(key = "d_${topic.id}") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add source") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        )
    }

    if (adding) {
        AddSourceDialog(vm, initial = pending.orEmpty(), onDismiss = { adding = false; MainActivity.pendingAdd.value = null })
    }
    editing?.let { src ->
        val live = sources.firstOrNull { it.id == src.id } ?: src
        SourceDialog(vm, live, statuses[src.id], onDismiss = { editing = null })
    }
}

@Composable
private fun TopicRow(
    topic: Topic, sources: List<Source>, followed: Boolean, muted: Boolean, weight: Float, expanded: Boolean,
    onExpand: () -> Unit, onFollow: (Boolean) -> Unit, onWeight: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(topic.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(topic.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    (if (muted) "Hidden · " else "") + "${sources.count { it.enabled }} of ${sources.size} sources on",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse" else "Expand", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Switch(checked = followed && !muted, onCheckedChange = onFollow)
        }
        if (expanded && followed && !muted) {
            Spacer(Modifier.height(8.dp))
            val options = listOf(0.5f to "Less", 1f to "Normal", 1.6f to "More")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 40.dp)) {
                options.forEachIndexed { i, (w, label) ->
                    SegmentedButton(
                        selected = kotlin.math.abs(weight - w) < 0.05f, onClick = { onWeight(w) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(src: Source, status: SourceStatus?, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Favicon(src.site, 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(src.name + if (src.custom) "  ·  yours" else "", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status?.error != null) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(statusText(src, status), style = MaterialTheme.typography.bodySmall, color = if (status?.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = src.enabled, onCheckedChange = onToggle)
    }
}

private fun statusText(src: Source, st: SourceStatus?): String {
    val base = src.type.label + when {
        src.weight > 1.2f -> " · more"
        src.weight < 0.8f -> " · less"
        else -> ""
    }
    return when {
        !src.enabled -> "$base · off"
        st == null || st.lastTry == 0L -> "$base · not fetched yet"
        st.error != null -> "$base · ${st.error}"
        else -> "$base · ${st.lastCount} stories · ${shortAge(st.lastOk)} ago"
    }
}

@Composable
private fun SourceDialog(vm: MainViewModel, src: Source, status: SourceStatus?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var include by remember(src.id) { mutableStateOf(src.include) }
    var exclude by remember(src.id) { mutableStateOf(src.exclude) }
    var weight by remember(src.id) { mutableStateOf(src.weight) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(src.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${src.type.label} · ${Catalog.topicById[src.topic]?.name ?: src.topic}", style = MaterialTheme.typography.bodyMedium)
                Text(statusText(src, status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("How often: ${weightLabel(weight)}", style = MaterialTheme.typography.labelLarge)
                androidx.compose.material3.Slider(value = weight, onValueChange = { weight = it }, valueRange = 0.2f..2.5f)
                OutlinedTextField(
                    value = include, onValueChange = { include = it }, label = { Text("Only stories with these words") },
                    placeholder = { Text("e.g. rtx|ryzen  or  re:regex") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = exclude, onValueChange = { exclude = it }, label = { Text("Skip stories with these words") },
                    placeholder = { Text("e.g. rumour|leak") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.setMode(Mode.FromSource(src.id)); MainActivity.tab.value = 0; onDismiss() }) { Text("Show only this") }
                    TextButton(onClick = { Links.openExternal(ctx, if (src.site.isNotBlank()) "https://${src.site}" else src.url) }) { Text("Open site") }
                    TextButton(onClick = { Links.copy(ctx, src.url) }) { Text("Copy feed URL") }
                    if (src.custom) TextButton(onClick = { vm.repo.removeSource(src.id); onDismiss() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.repo.editSource(src.id) { it.copy(include = include.trim(), exclude = exclude.trim(), weight = (weight * 10).toInt() / 10f) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun weightLabel(w: Float) = when {
    w < 0.45f -> "much less"
    w < 0.85f -> "less"
    w <= 1.2f -> "normal"
    w <= 1.8f -> "more"
    else -> "much more"
}

@Composable
fun AddSourceDialog(vm: MainViewModel, initial: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initial) }
    var topic by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Discover.Found?>(null) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (found == null) "Add a source" else "Add ${found!!.source.type.label} source?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (found == null) {
                    Text(
                        "Paste a website or feed link, r/subreddit, a YouTube channel (@name or link), a GitHub repo, " +
                            "a Steam game link, or type keywords to follow them as a Google News search.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input, onValueChange = { input = it; error = null }, singleLine = true,
                        label = { Text("Link, r/name, @channel or keywords") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Topic", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = topic == null, onClick = { topic = null }, label = { Text("Guess for me") })
                        Catalog.topics.forEach { t ->
                            FilterChip(selected = topic == t.id, onClick = { topic = t.id }, label = { Text("${t.emoji} ${t.name}") })
                        }
                    }
                } else {
                    val f = found!!
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Topic: ${Catalog.topicById[f.source.topic]?.name ?: f.source.topic}", style = MaterialTheme.typography.bodyMedium)
                    Text(f.source.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Text("Latest stories:", style = MaterialTheme.typography.labelLarge)
                    f.sampleTitles.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Looking for stories…")
                    }
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp)) {
                        Text(it, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            if (found == null) {
                TextButton(enabled = input.isNotBlank() && !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val f = vm.discover(input, topic)
                            found = f; name = f.source.name
                        } catch (e: Exception) {
                            error = e.message ?: "Couldn't add that (${e.javaClass.simpleName})"
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Find") }
            } else {
                TextButton(onClick = { vm.addSource(found!!.source.copy(name = name.ifBlank { found!!.source.name })); onDismiss() }) { Text("Add") }
            }
        },
        dismissButton = { TextButton(onClick = { if (found != null) found = null else onDismiss() }) { Text(if (found != null) "Back" else "Cancel") } },
    )
}
