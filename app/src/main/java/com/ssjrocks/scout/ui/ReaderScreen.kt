package com.ssjrocks.scout.ui

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssjrocks.scout.net.Article
import java.util.Locale

@Composable
fun ReaderScreen(vm: MainViewModel, screen: Screen.Reader) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val state by vm.reader.collectAsStateWithLifecycle()
    var speaking by remember { mutableStateOf(false) }
    val dark = isDark(s)
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    LaunchedEffect(screen) { vm.loadReader(screen.itemId, screen.url) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (utteranceId == "end") speaking = false }
                    @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { speaking = false }
                })
                tts.value = engine
            }
        }
        onDispose { engine?.stop(); engine?.shutdown() }
    }

    fun speak(a: Article) {
        val t = tts.value ?: return
        if (speaking) { t.stop(); speaking = false; return }
        speaking = true
        t.speak(a.title, TextToSpeech.QUEUE_FLUSH, null, "title")
        a.blocks.filter { it.kind in setOf("p", "h", "quote", "li") }.forEachIndexed { i, b ->
            t.speak(b.text, TextToSpeech.QUEUE_ADD, null, "b$i")
        }
        t.speak(" ", TextToSpeech.QUEUE_ADD, null, "end")
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                val site = (state as? ReaderState.Ready)?.article?.site ?: ""
                Text(site, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            },
            navigationIcon = { IconButton(onClick = { tts.value?.stop(); vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                val ready = state as? ReaderState.Ready
                if (ready != null) {
                    IconButton(onClick = { speak(ready.article) }) {
                        Icon(if (speaking) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp, if (speaking) "Stop reading" else "Read aloud")
                    }
                }
                IconButton(onClick = {
                    val steps = listOf(0.9f, 1f, 1.15f, 1.3f, 1.5f)
                    val next = steps.firstOrNull { it > s.readerScale + 0.01f } ?: steps.first()
                    vm.repo.update { it.copy(readerScale = next) }
                }) { Icon(Icons.Outlined.TextFields, "Text size") }
                IconButton(onClick = { Links.share(ctx, (state as? ReaderState.Ready)?.article?.title ?: "", screen.url) }) { Icon(Icons.Outlined.Share, "Share") }
                IconButton(onClick = { Links.openExternal(ctx, screen.url) }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open original") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        when (val st = state) {
            ReaderState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ReaderState.Failed -> EmptyState("📄", "Reader view isn't available", st.message) {
                Button(onClick = { Links.open(ctx, screen.url, s, dark) }) { Text("Open the original page") }
            }
            is ReaderState.Ready -> ArticleBody(st.article, s.readerScale, s.readerSerif) { Links.open(ctx, screen.url, s, dark) }
        }
    }
}

@Composable
private fun ArticleBody(a: Article, scale: Float, serif: Boolean, onOriginal: () -> Unit) {
    val family = if (serif) FontFamily.Serif else GoogleSans
    val body = MaterialTheme.typography.bodyLarge.copy(fontFamily = family, fontSize = (17 * scale).sp, lineHeight = (27 * scale).sp)
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        item {
            Text(a.title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = (26 * scale).sp, lineHeight = (33 * scale).sp), fontWeight = FontWeight.Normal)
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(a.byline, a.site, "${a.minutes} min read").joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (a.image != null) {
                RemoteImage(listOf(a.image), Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.FillWidth)
                Spacer(Modifier.height(16.dp))
            }
        }
        items(a.blocks) { b ->
            when (b.kind) {
                "h" -> Text(b.text, style = MaterialTheme.typography.titleLarge.copy(fontSize = (21 * scale).sp), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                "img" -> if (b.url != null) {
                    RemoteImage(listOf(b.url), Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.FillWidth)
                }
                "caption" -> Text(b.text, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                "quote" -> Row(Modifier.padding(vertical = 8.dp).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(12.dp))
                    Text(b.text, style = body.copy(fontStyle = FontStyle.Italic))
                }
                "li" -> Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("•", style = body)
                    Text(b.text, style = body)
                }
                "code" -> Text(
                    b.text, fontFamily = FontFamily.Monospace, fontSize = (13 * scale).sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer).horizontalScroll(rememberScrollState()).padding(12.dp),
                )
                else -> Text(b.text, style = body, modifier = Modifier.padding(vertical = 7.dp))
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onOriginal, modifier = Modifier.fillMaxWidth()) { Text("Open the original article") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
