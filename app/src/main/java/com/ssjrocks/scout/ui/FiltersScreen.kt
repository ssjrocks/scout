package com.ssjrocks.scout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssjrocks.scout.data.Catalog
import com.ssjrocks.scout.data.Kinds
import com.ssjrocks.scout.data.SourceType

private val kindRows = listOf(
    Triple(Kinds.X, "Posts from X / Twitter", "Anything linking to x.com or twitter.com, from any source"),
    Triple(Kinds.X_MENTION, "Stories about X posts", "Headlines built around tweets: “posted on X”, “tweeted”…"),
    Triple(Kinds.SOCIAL, "Other social media posts", "Facebook, Instagram, TikTok, Threads, Bluesky, LinkedIn, Kick"),
    Triple(Kinds.SHORTS, "YouTube Shorts", "Keep full videos, drop Shorts"),
    Triple(Kinds.VIDEO, "All videos", "YouTube, Vimeo, Twitch and Reddit videos"),
    Triple(Kinds.PAYWALL, "Paywalled sites", "Courier-Mail, The Australian, AFR, Brisbane Times, NYT, WSJ…"),
    Triple(Kinds.CLICKBAIT, "Clickbait headlines", "“You won't believe…”, “Here's why…”, “😱”, list-bait"),
    Triple(Kinds.SHOPPING, "Shopping & deal articles", "“% off”, “lowest price”, Prime Day posts on news sites"),
    Triple(Kinds.NO_IMAGE, "Stories without a picture", "Text-only items (HN, release notes, some Reddit)"),
)

@Composable
fun FiltersScreen(vm: MainViewModel, padding: PaddingValues) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val states by vm.repo.states.collectAsStateWithLifecycle()
    val hiddenCount = remember(states) { states.values.count { it.hidden > 0 } }
    val offSources = remember(s) {
        vm.repo.allSources(s).filter { src -> !src.enabled && (src.custom || Catalog.sourceById[src.id]?.enabled == true) }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Text("Filters & blocking", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Hide whole kinds of content, whatever source they come from. ⋮ on any story has quick versions of these.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SectionTitle("Hide these kinds of content") }
        kindRows.forEach { (kind, title, sub) ->
            item(key = "k_$kind") {
                SwitchRow(title, sub, checked = kind in s.blockedKinds) { on ->
                    vm.repo.update { it.copy(blockedKinds = if (on) it.blockedKinds + kind else it.blockedKinds - kind) }
                }
            }
        }
        item { SectionTitle("Hide whole source types") }
        SourceType.entries.forEach { type ->
            item(key = "t_${type.name}") {
                SwitchRow("Hide ${type.plural}", null, checked = type.name in s.blockedTypes) { on ->
                    vm.repo.update { it.copy(blockedTypes = if (on) it.blockedTypes + type.name else it.blockedTypes - type.name) }
                }
            }
        }
        item { SectionTitle("Muted words") }
        item {
            Text(
                "Stories with these words in the headline or summary are hidden. Whole words, any case. Start with re: for a regular expression.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
            )
            WordListEditor(s.mutedWords, "Add a word or phrase", onAdd = { vm.repo.muteWord(it) }, onRemove = { w -> vm.repo.update { it.copy(mutedWords = it.mutedWords - w) } })
        }
        item { SectionTitle("Blocked sites") }
        item {
            Text(
                "Stories from these sites never show, including via Google News, Reddit and Hacker News links.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
            )
            WordListEditor(
                s.blockedDomains.sorted(), "example.com",
                onAdd = { d -> vm.repo.blockDomain(d.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')) },
                onRemove = { d -> vm.repo.update { it.copy(blockedDomains = it.blockedDomains - d) } },
            )
        }
        if (s.mutedTopics.isNotEmpty()) {
            item { SectionTitle("Hidden topics") }
            item {
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.mutedTopics.forEach { t ->
                        InputChip(
                            selected = false, onClick = { vm.repo.update { it.copy(mutedTopics = it.mutedTopics - t) } },
                            label = { Text(Catalog.topicById[t]?.let { "${it.emoji} ${it.name}" } ?: t) },
                            trailingIcon = { Icon(Icons.Outlined.Close, "Unhide", Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }
        if (offSources.isNotEmpty()) {
            item { SectionTitle("Sources you turned off") }
            offSources.forEach { src ->
                item(key = "o_${src.id}") {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Favicon(src.site, 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(src.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.repo.editSource(src.id) { it.copy(enabled = true) } }) { Text("Turn on") }
                    }
                }
            }
        }
        item { SectionTitle("Feed") }
        item {
            SliderRow(
                "Oldest story to show", s.maxAgeHours.toFloat(), 12f..168f, steps = 12,
                label = { h -> if (h >= 48) "${(h / 24).toInt()} days" else "${h.toInt()} hours" },
            ) { v -> vm.repo.update { it.copy(maxAgeHours = v.toInt()) } }
        }
        item { SwitchRow("Hide stories I've read", null, s.hideRead) { v -> vm.repo.update { it.copy(hideRead = v) } } }
        item { SwitchRow("Fade stories I've read", null, s.dimRead) { v -> vm.repo.update { it.copy(dimRead = v) } } }
        item { SwitchRow("Group the same story (Full coverage)", "Folds matching headlines from different sources into one card", s.clusterStories) { v -> vm.repo.update { it.copy(clusterStories = v) } } }
        item { SwitchRow("Mix sources", "Avoid several cards in a row from one source", s.mixSources) { v -> vm.repo.update { it.copy(mixSources = v) } } }
        item { SwitchRow("Learn from what I read", "Opening, liking and hiding stories adjusts topics and sources", s.learn) { v -> vm.repo.update { it.copy(learn = v) } } }
        if (hiddenCount > 0) {
            item {
                ClickRow("Show hidden stories again", "$hiddenCount stories hidden") { vm.repo.clearHidden(); vm.snack("Hidden stories restored") }
            }
        }
    }
}
