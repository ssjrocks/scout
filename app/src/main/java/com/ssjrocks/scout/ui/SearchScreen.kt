package com.ssjrocks.scout.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SearchScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val trends by vm.trends.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val dark = isDark(s)
    val local = remember(q) { if (q.length >= 3) vm.localResults(q) else emptyList() }

    fun submit(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        vm.repo.addSearch(t)
        val looksLikeUrl = !t.contains(' ') && (t.startsWith("http") || Regex("^[\\w-]+(\\.[\\w-]+)+(/.*)?$").matches(t))
        if (looksLikeUrl) Links.open(ctx, if (t.startsWith("http")) t else "https://$t", s, dark) else Links.search(ctx, t, s, dark)
        vm.query.value = ""
        vm.pop()
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { submit(it) }
    }

    LaunchedEffect(Unit) {
        vm.loadTrends()
        runCatching { focus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Surface(
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.query.value = ""; vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                BasicTextField(
                    value = q, onValueChange = { vm.query.value = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(q) }),
                    modifier = Modifier.weight(1f).padding(vertical = 16.dp).focusRequester(focus),
                    decorationBox = { inner ->
                        if (q.isEmpty()) Text("Search or type a URL", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    },
                )
                if (q.isNotEmpty()) {
                    IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Outlined.Close, "Clear") }
                } else {
                    IconButton(onClick = {
                        try {
                            voice.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search Google"),
                            )
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(ctx, "Voice input isn't available", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Outlined.Mic, "Voice search") }
                    IconButton(onClick = { Links.lens(ctx) }) { Icon(Icons.Outlined.CameraAlt, "Google Lens") }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (q.isBlank()) {
                if (s.searchHistory.isNotEmpty()) {
                    item { Header("Recent") }
                    items(s.searchHistory.take(8), key = { "h_$it" }) { h ->
                        SuggestionRow(Icons.Outlined.History, h, onClick = { submit(h) }, onFill = null, onRemove = {
                            vm.repo.update { st -> st.copy(searchHistory = st.searchHistory - h) }
                        })
                    }
                }
                if (trends.isNotEmpty()) {
                    item { Header("Trending searches in Australia") }
                    items(trends.take(12), key = { "t_${it.query}" }) { t ->
                        Row(Modifier.fillMaxWidth().clickable { submit(t.query) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Outlined.TrendingUp, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.query, style = MaterialTheme.typography.bodyLarge)
                                val sub = listOfNotNull(t.traffic?.let { "$it searches" }, t.newsTitle).joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else {
                items(suggestions, key = { "s_$it" }) { sug ->
                    SuggestionRow(Icons.Outlined.Search, sug, onClick = { submit(sug) }, onFill = { vm.query.value = "$sug " }, onRemove = null)
                }
                if (local.isNotEmpty()) {
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); Header("In your feed") }
                    items(local, key = { "l_${it.id}" }) { e ->
                        MiniStoryRow(e, onOpen = {
                            vm.repo.opened(e)
                            Links.open(ctx, e.targetUrl, s, dark)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun SuggestionRow(icon: ImageVector, text: String, onClick: () -> Unit, onFill: (() -> Unit)?, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onFill != null) IconButton(onClick = onFill) { Icon(Icons.Outlined.NorthWest, "Use", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
        if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
    }
}
