package com.ssjrocks.scout.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ssjrocks.scout.R
import com.ssjrocks.scout.ScoutApp
import com.ssjrocks.scout.data.WidgetStory
import com.ssjrocks.scout.ui.MainActivity
import com.ssjrocks.scout.ui.shortAge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home-screen widget: a Google-style search pill over your top unread stories. */
class ScoutWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stories = (context.applicationContext as ScoutApp).repo.widgetStories()
        provideContent { GlanceTheme { Content(context, stories) } }
    }

    @Composable
    private fun Content(context: Context, stories: List<WidgetStory>) {
        val search = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_SEARCH)
        Column(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(GlanceTheme.colors.widgetBackground).padding(12.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(48.dp).cornerRadius(24.dp)
                    .background(GlanceTheme.colors.secondaryContainer).padding(horizontal = 14.dp)
                    .clickable(actionStartActivity(search)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(ImageProvider(R.drawable.ic_google_g), contentDescription = null, modifier = GlanceModifier.size(22.dp))
                Spacer(GlanceModifier.width(12.dp))
                Text("Search", style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 16.sp), modifier = GlanceModifier.defaultWeight())
                Image(ImageProvider(R.drawable.ic_mic), contentDescription = "Voice search", modifier = GlanceModifier.size(22.dp))
            }
            Spacer(GlanceModifier.height(8.dp))
            if (stories.isEmpty()) {
                Text(
                    "Open Scout to load your feed",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                    modifier = GlanceModifier.padding(8.dp).clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                )
            } else {
                LazyColumn {
                    items(stories, itemId = { it.id.hashCode().toLong() }) { s ->
                        val open = Intent(context, MainActivity::class.java)
                            .setAction(MainActivity.ACTION_OPEN_STORY)
                            .setData(Uri.parse("scout://story/${s.id}"))
                            .putExtra(MainActivity.EXTRA_URL, s.link)
                            .putExtra(MainActivity.EXTRA_ID, s.id)
                        Column(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp).clickable(actionStartActivity(open))) {
                            Text(
                                s.title, maxLines = 2,
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            )
                            Text(
                                "${s.source} · ${shortAge(s.published)}", maxLines = 1,
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            CoroutineScope(Dispatchers.Default).launch { runCatching { ScoutWidget().updateAll(context) } }
        }
    }
}

class ScoutWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScoutWidget()
}
