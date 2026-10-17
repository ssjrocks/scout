package com.ssjrocks.scout.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssjrocks.scout.rank.Entry
import com.ssjrocks.scout.rank.Ranker

@Composable
fun SavedScreen(vm: MainViewModel, padding: PaddingValues) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val items by vm.repo.items.collectAsStateWithLifecycle()
    val states by vm.repo.states.collectAsStateWithLifecycle()
    val dark = isDark(s)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sources = remember(s) { vm.repo.sourceMap(s) }
    val entries = remember(items, states, tab, sources) {
        items.mapNotNull { item ->
            val st = states[item.id] ?: return@mapNotNull null
            val keep = if (tab == 0) st.saved > 0 else st.read > 0
            if (!keep) return@mapNotNull null
            val src = sources[item.sourceId] ?: return@mapNotNull null
            Entry(item, src, Ranker.topicsOf(item, src), 0.0, state = st)
        }.sortedByDescending { if (tab == 0) it.state.saved else it.state.read }
    }
    var menuFor by remember { androidx.compose.runtime.mutableStateOf<Entry?>(null) }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        Text("Saved", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Saved stories") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp)) {
            if (tab == 1 && entries.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${entries.size} stories read", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        TextButton(onClick = { vm.repo.clearHistory() }) { Text("Clear history") }
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    if (tab == 0) EmptyState("🔖", "Nothing saved yet", "Swipe a story right, or use ⋮ → Save for later. Saved stories are kept offline in reader view.")
                    else EmptyState("🕘", "No history", "Stories you open show up here.")
                }
            }
            items(entries, key = { it.id }) { e ->
                MiniStoryRow(e, onOpen = {
                    vm.repo.opened(e)
                    if (tab == 0) vm.push(Screen.Reader(e.id, e.targetUrl)) else Links.open(ctx, e.targetUrl, s, dark)
                }) {
                    if (tab == 0) {
                        IconButton(onClick = { vm.toggleSave(e) }) { SaveIcon(true) }
                    } else {
                        IconButton(onClick = { vm.push(Screen.Reader(e.id, e.targetUrl)) }) { Icon(Icons.Outlined.ChromeReaderMode, "Reader view") }
                    }
                }
            }
        }
    }
    menuFor?.let { StorySheet(vm, it, s) { menuFor = null } }
}
