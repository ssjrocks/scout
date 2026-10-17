package com.ssjrocks.scout.ui

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.ssjrocks.scout.data.Settings
import com.ssjrocks.scout.net.FeedParser
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun shortAge(ms: Long, now: Long = System.currentTimeMillis()): String {
    val m = (now - ms) / 60_000
    return when {
        m < 1 -> "now"
        m < 60 -> "${m}m"
        m < 24 * 60 -> "${m / 60}h"
        m < 7 * 24 * 60 -> "${m / (24 * 60)}d"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
    }
}

fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> "%.1fK".format(n / 1000.0).replace(".0K", "K")
    else -> n.toString()
}

fun faviconUrl(domain: String) = "https://www.google.com/s2/favicons?domain=$domain&sz=64"

object Links {
    private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"

    fun open(ctx: Context, url: String, s: Settings, dark: Boolean) {
        val host = FeedParser.hostOf(url)
        val wantsApp = s.openIn == "browser" ||
            (s.youtubeApp && (host == "youtube.com" || host == "youtu.be" || host.endsWith(".youtube.com")))
        if (wantsApp) {
            openExternal(ctx, url)
            return
        }
        try {
            val bar = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(if (dark) 0xFF1F1F1F.toInt() else 0xFFFFFFFF.toInt())
                .build()
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setColorScheme(if (dark) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
                .setDefaultColorSchemeParams(bar)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
                .launchUrl(ctx, Uri.parse(url))
        } catch (e: ActivityNotFoundException) {
            openExternal(ctx, url)
        }
    }

    fun openExternal(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(ctx: Context, title: String, url: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        ctx.startActivity(Intent.createChooser(send, "Share story").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("link", text))
        Toast.makeText(ctx, "Link copied", Toast.LENGTH_SHORT).show()
    }

    fun searchUrl(engine: String, q: String): String {
        val e = URLEncoder.encode(q, "UTF-8")
        return when (engine) {
            "ddg" -> "https://duckduckgo.com/?q=$e"
            "startpage" -> "https://www.startpage.com/do/search?q=$e"
            "bing" -> "https://www.bing.com/search?q=$e"
            else -> "https://www.google.com/search?q=$e"
        }
    }

    fun search(ctx: Context, q: String, s: Settings, dark: Boolean) {
        if (s.searchEngine == "google" && s.searchInGoogleApp) {
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_WEB_SEARCH).setPackage(GOOGLE_APP).putExtra(SearchManager.QUERY, q)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return
            } catch (_: Exception) {
            }
        }
        open(ctx, searchUrl(s.searchEngine, q), s.copy(openIn = "tab", youtubeApp = false), dark)
    }

    fun lens(ctx: Context) {
        val tries = listOf(
            { Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).setPackage(GOOGLE_APP) },
            { ctx.packageManager.getLaunchIntentForPackage("com.google.ar.lens") ?: throw ActivityNotFoundException() },
            { Intent(Intent.ACTION_VIEW, Uri.parse("https://lens.google.com/")) },
        )
        for (t in tries) {
            try {
                ctx.startActivity(t().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
    }

    fun isMetered(ctx: Context): Boolean =
        ctx.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
}
