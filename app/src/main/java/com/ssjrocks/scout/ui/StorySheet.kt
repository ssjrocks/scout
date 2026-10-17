package com.ssjrocks.scout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssjrocks.scout.data.Catalog
import com.ssjrocks.scout.data.Kinds
import com.ssjrocks.scout.data.Settings
import com.ssjrocks.scout.rank.Entry
import com.ssjrocks.scout.rank.Ranker

@Composable
fun StorySheet(vm: MainViewModel, e: Entry, s: Settings, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val dark = isDark(s)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var whyOpen by remember { mutableStateOf(false) }
    var muteOpen by remember { mutableStateOf(false) }
    val kinds = remember(e.id) { Ranker.kindsOf(e.item, e.source) }
    val publisher = e.item.publisher
    val siteDomain = e.item.domain.takeIf { it.isNotBlank() && it != e.source.site && it != "reddit.com" }
    fun done(f: () -> Unit) { f(); onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Favicon(if (publisher != null) e.item.domain else e.source.site.ifBlank { e.item.domain }, 20.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(publisher ?: e.source.name, style = MaterialTheme.typography.titleSmall)
                    Text(e.item.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.size(8.dp))
            if (Kinds.VIDEO !in kinds) {
                SheetRow(Icons.Outlined.ChromeReaderMode, "Open in reader view") { done { vm.repo.opened(e); vm.push(Screen.Reader(e.id, e.targetUrl)) } }
            }
            SheetRow(if (e.state.saved > 0) Icons.Outlined.RemoveCircleOutline else Icons.Outlined.BookmarkAdd, if (e.state.saved > 0) "Remove from saved" else "Save for later") {
                done { vm.toggleSave(e) }
            }
            SheetRow(Icons.Outlined.Share, "Share") { done { Links.share(ctx, e.item.title, e.targetUrl) } }
            SheetRow(Icons.AutoMirrored.Outlined.OpenInNew, "Open in browser app") { done { vm.repo.opened(e); Links.openExternal(ctx, e.targetUrl) } }
            SheetRow(Icons.Outlined.ContentCopy, "Copy link") { done { Links.copy(ctx, e.targetUrl) } }
            e.item.commentsUrl?.takeIf { it != e.targetUrl }?.let { c ->
                SheetRow(Icons.Outlined.ChatBubbleOutline, "Open comments") { done { Links.open(ctx, c, s, dark) } }
            }
            if (e.state.read > 0) SheetRow(Icons.Outlined.MarkEmailUnread, "Mark as unread") { done { vm.repo.markUnread(e.id) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Tune your feed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            SheetRow(Icons.Outlined.ThumbUp, if (e.state.liked == 1) "Liked: more stories like this" else "More stories like this") { done { if (e.state.liked != 1) vm.repo.toggleLike(e); vm.snack("You'll see more like this") } }
            SheetRow(Icons.Outlined.ThumbDown, "Fewer stories like this") { done { vm.lessLike(e) } }
            SheetRow(Icons.Outlined.VisibilityOff, "Hide this story") { done { vm.hide(e) } }
            SheetRow(Icons.Outlined.Block, "Don't show stories from ${publisher ?: e.source.name}") { done { vm.blockSource(e) } }
            if (siteDomain != null && publisher == null) {
                SheetRow(Icons.Outlined.Block, "Block the site $siteDomain everywhere") { done { vm.blockDomain(siteDomain) } }
            }
            e.topics.take(3).forEach { t ->
                val name = Catalog.topicById[t]?.name ?: t
                SheetRow(Icons.Outlined.DoNotDisturbOn, "Not interested in $name") { done { vm.muteTopic(t, name) } }
            }
            SheetRow(Icons.Outlined.DoNotDisturbOn, "Hide all ${e.source.type.plural}") { done { vm.blockType(e) } }
            if (Kinds.X in kinds && Kinds.X !in s.blockedKinds) SheetRow(Icons.Outlined.Block, "Hide all X / Twitter posts") { done { vm.blockKind(Kinds.X, "X posts") } }
            if (Kinds.X_MENTION in kinds && Kinds.X_MENTION !in s.blockedKinds) SheetRow(Icons.Outlined.Block, "Hide stories about X / Twitter posts") { done { vm.blockKind(Kinds.X_MENTION, "Stories about X posts") } }
            if (Kinds.SOCIAL in kinds) SheetRow(Icons.Outlined.Block, "Hide all social media posts") { done { vm.blockKind(Kinds.SOCIAL, "Social media posts") } }
            if (Kinds.SHORTS in kinds) SheetRow(Icons.Outlined.Block, "Hide all YouTube Shorts") { done { vm.blockKind(Kinds.SHORTS, "YouTube Shorts") } }
            if (Kinds.VIDEO in kinds) SheetRow(Icons.Outlined.Block, "Hide all videos") { done { vm.blockKind(Kinds.VIDEO, "Videos") } }
            if (Kinds.PAYWALL in kinds) SheetRow(Icons.Outlined.Block, "Hide paywalled sites") { done { vm.blockKind(Kinds.PAYWALL, "Paywalled stories") } }
            if (Kinds.CLICKBAIT in kinds) SheetRow(Icons.Outlined.Block, "Hide clickbait headlines") { done { vm.blockKind(Kinds.CLICKBAIT, "Clickbait headlines") } }
            if (Kinds.SHOPPING in kinds) SheetRow(Icons.Outlined.Block, "Hide shopping and deal articles") { done { vm.blockKind(Kinds.SHOPPING, "Deal articles") } }
            SheetRow(Icons.Outlined.FilterAlt, "Mute a word from this headline…") { muteOpen = true }
            SheetRow(Icons.Outlined.TrendingUp, "Show more from ${e.source.name}") { done { vm.moreFromSource(e, true) } }
            SheetRow(Icons.Outlined.TrendingDown, "Show less from ${e.source.name}") { done { vm.moreFromSource(e, false) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetRow(Icons.Outlined.Info, "Why you're seeing this", trailing = if (whyOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore) { whyOpen = !whyOpen }
            if (whyOpen) {
                Column(Modifier.padding(start = 60.dp, end = 20.dp, bottom = 8.dp)) {
                    (e.why.ifEmpty { listOf("From ${e.source.name}") }).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.topics.isNotEmpty()) {
                        Text(
                            "• Topics: " + e.topics.joinToString { Catalog.topicById[it]?.name ?: it },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (muteOpen) MuteWordDialog(e.item.title, onDismiss = { muteOpen = false }) { w -> muteOpen = false; onDismiss(); vm.muteWord(w) }
}

@Composable
private fun SheetRow(icon: ImageVector, text: String, trailing: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (trailing != null) Icon(trailing, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Pick one or more words (or a phrase) from the headline to mute everywhere. */
@Composable
private fun MuteWordDialog(title: String, onDismiss: () -> Unit, onMute: (String) -> Unit) {
    val words = remember(title) {
        title.split(Regex("\\s+")).map { it.trim('"', '\'', '‘', '’', '“', '”', ',', '.', ':', ';', '!', '?', '(', ')', '[', ']') }
            .filter { it.length > 2 }.distinct()
    }
    val picked = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mute words") },
        text = {
            Column {
                Text("Stories containing the picked words are hidden. Picking several words mutes that phrase.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    words.forEach { w ->
                        FilterChip(selected = w in picked, onClick = { if (w in picked) picked.remove(w) else picked.add(w) }, label = { Text(w) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty(), onClick = { onMute(words.filter { it in picked }.joinToString(" ")) }) { Text("Mute") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
