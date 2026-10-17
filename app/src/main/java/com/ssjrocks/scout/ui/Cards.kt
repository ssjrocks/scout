package com.ssjrocks.scout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssjrocks.scout.data.Item
import com.ssjrocks.scout.data.Settings
import com.ssjrocks.scout.data.Source
import com.ssjrocks.scout.data.SourceType
import com.ssjrocks.scout.rank.Entry

/** Where tapping a story goes: the article for Reddit link posts, the post itself otherwise. */
val Entry.targetUrl: String
    get() = if (source.type == SourceType.REDDIT && item.extUrl != null) item.extUrl else item.link

class CardActions(
    val open: (Entry) -> Unit,
    val openUrl: (String) -> Unit,
    val like: (Entry) -> Unit,
    val share: (Entry) -> Unit,
    val save: (Entry) -> Unit,
    val hide: (Entry) -> Unit,
    val menu: (Entry) -> Unit,
)

@Composable
fun StoryCard(e: Entry, s: Settings, a: CardActions) {
    if (!s.swipeActions) {
        CardBody(e, s, a)
        return
    }
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.35f })
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> { a.save(e); state.reset() }
            SwipeToDismissBoxValue.EndToStart -> a.hide(e)
            SwipeToDismissBoxValue.Settled -> {}
        }
    }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toSave = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val color = if (toSave) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            Box(
                Modifier.fillMaxSize().background(color).padding(horizontal = 28.dp),
                contentAlignment = if (toSave) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (toSave) Icons.Filled.Bookmark else Icons.Outlined.VisibilityOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (toSave) (if (e.state.saved > 0) "Unsave" else "Save") else "Hide")
                }
            }
        },
    ) {
        CardBody(e, s, a)
    }
}

@Composable
private fun CardBody(e: Entry, s: Settings, a: CardActions) {
    val big = e.item.image != null && !e.item.imageSmall && !s.compactCards && e.source.type != SourceType.DEALS
    val dim = s.dimRead && e.state.read > 0
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = { a.open(e) }, onLongClick = { a.menu(e) })
            .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 2.dp)
            .alpha(if (dim) 0.62f else 1f),
    ) {
        if (big) BigLayout(e) else CompactLayout(e)
        MetaRow(e, a)
        if (e.related.isNotEmpty()) FullCoverage(e.related, a)
    }
}

@Composable
private fun BigLayout(e: Entry) {
    var failed by remember(e.item.image) { mutableStateOf(false) }
    Box(Modifier.padding(end = 12.dp)) {
        if (!failed && LocalImagesOn.current) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                RemoteImage(imageUrls(e.item.image, e.item.videoId), Modifier.fillMaxSize(), onAllFailed = { failed = true })
                if (e.item.videoId != null) {
                    Box(
                        Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.PlayArrow, "Video", tint = Color.White, modifier = Modifier.size(32.dp)) }
                    if (e.item.isShort) Badge("Shorts", Modifier.align(Alignment.TopStart).padding(10.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        e.item.title, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface, maxLines = 4, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(end = 12.dp),
    )
}

@Composable
private fun CompactLayout(e: Entry) {
    var failed by remember(e.item.image) { mutableStateOf(false) }
    val hasThumb = e.item.image != null && !failed && LocalImagesOn.current
    Row(Modifier.padding(end = 12.dp)) {
        Column(Modifier.weight(1f)) {
            if (e.source.type == SourceType.DEALS) DealLine(e.item)
            Text(
                e.item.title, fontSize = 17.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
            if (!hasThumb && e.item.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    e.item.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasThumb) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(92.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
                RemoteImage(imageUrls(e.item.image, e.item.videoId), Modifier.fillMaxSize(), onAllFailed = { failed = true })
            }
        }
    }
}

@Composable
private fun DealLine(item: Item) {
    Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item.price?.let { Badge(it, color = MaterialTheme.colorScheme.tertiaryContainer, onColor = MaterialTheme.colorScheme.onTertiaryContainer) }
        item.points?.let { Badge((if (it >= 0) "+" else "") + it, color = MaterialTheme.colorScheme.surfaceContainerHigh, onColor = MaterialTheme.colorScheme.onSurface) }
        item.expires?.let { exp ->
            val days = ((exp - System.currentTimeMillis()) / 86_400_000L).toInt()
            if (days in 0..14) Text(if (days == 0) "ends today" else "ends in ${days}d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Color.Black.copy(alpha = 0.6f), onColor: Color = Color.White) {
    Surface(shape = RoundedCornerShape(6.dp), color = color, modifier = modifier) {
        Text(text, color = onColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private fun metaText(e: Entry): String {
    val it = e.item
    val parts = ArrayList<String>()
    parts += it.publisher ?: e.source.name
    when (e.source.type) {
        SourceType.YOUTUBE -> it.points?.let { v -> parts += "${compactCount(v)} views" }
        SourceType.HN -> {
            it.points?.let { p -> parts += "$p pts" }
            if (it.domain.isNotBlank()) parts += it.domain
        }
        SourceType.REDDIT -> if (it.domain != "reddit.com" && it.domain.isNotBlank()) parts += it.domain
        SourceType.DEALS -> if (it.domain.isNotBlank() && it.domain != "ozbargain.com.au") parts += it.domain
        else -> {}
    }
    parts += shortAge(it.published)
    return parts.joinToString(" · ")
}

@Composable
private fun MetaRow(e: Entry, a: CardActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Favicon(if (e.item.publisher != null) e.item.domain else e.source.site.ifBlank { e.item.domain })
        Spacer(Modifier.width(8.dp))
        Text(
            metaText(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (e.state.saved > 0) Icon(Icons.Filled.Bookmark, "Saved", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        val comments = e.item.commentsUrl
        val showComments = comments != null &&
            (e.source.type == SourceType.HN || (e.source.type == SourceType.REDDIT && e.item.extUrl != null))
        if (showComments) {
            IconButton(onClick = { a.openUrl(comments!!) }) {
                Icon(Icons.Outlined.ChatBubbleOutline, "Comments", modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = { a.like(e) }) {
            if (e.state.liked == 1) Icon(Icons.Filled.Favorite, "Unlike", tint = Color(0xFFE8453C), modifier = Modifier.size(20.dp))
            else Icon(Icons.Outlined.FavoriteBorder, "Like", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { a.share(e) }) {
            Icon(Icons.Outlined.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { a.menu(e) }) {
            Icon(Icons.Outlined.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FullCoverage(related: List<Pair<Item, Source>>, a: CardActions) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) related else related.take(2)
    Column(Modifier.padding(end = 12.dp, bottom = 8.dp)) {
        Text(
            "Full coverage · ${related.size + 1} sources", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp),
        )
        shown.forEach { (item, src) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { a.openUrl(item.link) }
                    .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Favicon(if (item.publisher != null) item.domain else src.site.ifBlank { item.domain }, 14.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${item.publisher ?: src.name} · ${shortAge(item.published)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (related.size > 2) {
            Text(
                if (expanded) "Show less" else "+${related.size - 2} more", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(6.dp),
            )
        }
    }
}

/** Small row used in search results and the Saved/History lists. */
@Composable
fun MiniStoryRow(e: Entry, onOpen: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Favicon(if (e.item.publisher != null) e.item.domain else e.source.site.ifBlank { e.item.domain }, 14.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${e.item.publisher ?: e.source.name} · ${shortAge(e.item.published)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (e.item.image != null && LocalImagesOn.current) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
                RemoteImage(imageUrls(e.item.image, e.item.videoId), Modifier.fillMaxSize())
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun SaveIcon(saved: Boolean) {
    Icon(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, if (saved) "Unsave" else "Save")
}
