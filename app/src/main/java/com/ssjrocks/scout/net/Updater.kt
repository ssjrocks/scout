package com.ssjrocks.scout.net

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ssjrocks.scout.BuildConfig
import com.ssjrocks.scout.R
import com.ssjrocks.scout.ScoutApp
import com.ssjrocks.scout.ui.MainActivity
import com.ssjrocks.scout.work.UpdateReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val releaseUrl: String,
    val published: String,
    val notes: String,
)

/**
 * Updates from the project's GitHub releases. Each release carries `scout.apk` and `scout.json`
 * (versionCode, versionName, sha256). The APK must come from the same host as the release, must
 * match the checksum, and Android only installs it over this app if it's signed with the same key.
 */
object Updater {
    private const val TAG = "ScoutUpdate"
    const val RELEASES_PAGE = "https://github.com/ssjrocks/scout/releases"

    /** True while an activity is on screen (an installer prompt can then be shown directly). */
    @Volatile var foreground = false

    /** Install results (PackageInstaller status, message) for the screen that started the install. */
    val results = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Int, String?>>(extraBufferCapacity = 4)

    private val trustedHost: String =
        Uri.parse(BuildConfig.UPDATE_API).host.let { if (it == "api.github.com") "github.com" else it.orEmpty() }

    private val ua = "Scout/${BuildConfig.VERSION_NAME} (Android)"

    private fun trusted(url: String): String {
        require(Uri.parse(url).host == trustedHost) { "the release points somewhere other than $trustedHost" }
        return url
    }

    /** The newest release if it's newer than this build, else null. Throws on network errors. */
    suspend fun check(): AppUpdate? {
        val release = Json.parseToJsonElement(
            Http.get(BuildConfig.UPDATE_API, accept = "application/vnd.github+json", headers = mapOf("User-Agent" to ua)),
        ).jsonObject
        val assets = release["assets"]?.jsonArray?.map { it.jsonObject } ?: return null
        fun asset(name: String): JsonObject? = assets.firstOrNull { it["name"]?.jsonPrimitive?.content == name }
        val meta = asset("scout.json") ?: return null
        val apk = asset("scout.apk") ?: return null
        val info = Json.parseToJsonElement(
            Http.get(trusted(meta["browser_download_url"]!!.jsonPrimitive.content), headers = mapOf("User-Agent" to ua)),
        ).jsonObject
        val code = info["versionCode"]?.jsonPrimitive?.int ?: return null
        if (code <= BuildConfig.VERSION_CODE) return null
        return AppUpdate(
            versionName = info["versionName"]?.jsonPrimitive?.content ?: "?",
            versionCode = code,
            apkUrl = trusted(apk["browser_download_url"]!!.jsonPrimitive.content),
            sha256 = info["sha256"]?.jsonPrimitive?.content?.lowercase(Locale.ROOT).orEmpty(),
            size = apk["size"]?.jsonPrimitive?.long ?: 0,
            releaseUrl = release["html_url"]?.jsonPrimitive?.content ?: RELEASES_PAGE,
            published = formatDate(release["published_at"]?.jsonPrimitive?.content.orEmpty()),
            notes = plainText(release["body"]?.jsonPrimitive?.content.orEmpty()),
        )
    }

    private fun apkFile(ctx: Context, u: AppUpdate) = File(File(ctx.cacheDir, "updates").apply { mkdirs() }, "scout-${u.versionCode}.apk")

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Downloads (or reuses an already downloaded) APK and checks its SHA-256. [progress] gets 0..1. */
    suspend fun download(ctx: Context, u: AppUpdate, progress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val file = apkFile(ctx, u)
        if (file.exists() && (u.sha256.isBlank() || sha256(file) == u.sha256)) return@withContext file
        file.parentFile?.listFiles()?.forEach { it.delete() }
        // No HTTP cache and no overall time limit: the APK is several MB on a phone connection.
        val client = Http.client.newBuilder().cache(null).callTimeout(0, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        val req = Request.Builder().url(u.apkUrl).header("User-Agent", ua).build()
        try {
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw HttpException(r.code, "download failed (HTTP ${r.code})")
                val body = r.body ?: throw IllegalStateException("empty download")
                val total = body.contentLength().takeIf { it > 0 } ?: u.size
                val md = MessageDigest.getInstance("SHA-256")
                var read = 0L
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            md.update(buf, 0, n)
                            read += n
                            if (total > 0) progress(read.toFloat() / total)
                        }
                    }
                }
                val sha = md.digest().joinToString("") { "%02x".format(it) }
                if (u.sha256.isNotBlank() && sha != u.sha256) {
                    throw IllegalStateException("the download doesn't match the release's checksum, so it wasn't installed")
                }
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        file
    }

    /**
     * Android 12+ lets an app update itself without a prompt when it installed the current
     * version itself and is allowed to install apps.
     */
    fun canInstallSilently(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val pm = ctx.packageManager
        val installer = runCatching { pm.getInstallSourceInfo(ctx.packageName).installingPackageName }.getOrNull()
        return installer == ctx.packageName && pm.canRequestPackageInstalls()
    }

    /**
     * Hands the APK to Android's package installer. The result goes to [UpdateReceiver], which
     * shows Android's confirmation screen when one is needed. On success Android stops this app
     * and installs the new version.
     */
    fun install(ctx: Context, file: File, u: AppUpdate) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("scout.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            ctx.getSharedPreferences("scout", Context.MODE_PRIVATE).edit()
                .putInt("installing_code", u.versionCode).putString("installing_name", u.versionName).apply()
            val status = Intent(ctx, UpdateReceiver::class.java).setAction(UpdateReceiver.ACTION_STATUS)
                .putExtra(UpdateReceiver.EXTRA_VERSION, u.versionName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(ctx, id, status, flags).intentSender)
        }
        Log.i(TAG, "install session $id committed for ${u.versionName}")
    }

    /**
     * Background step, run after a scheduled refresh: at most every 12 hours, look for a release,
     * download it, and install it silently when Android allows; otherwise post a notification.
     */
    suspend fun backgroundUpdate(ctx: Context, skipCode: Int) {
        val prefs = ctx.getSharedPreferences("scout", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("update_checked_at", 0) < 12 * 3_600_000L) return
        prefs.edit().putLong("update_checked_at", now).apply()
        val u = check() ?: return
        if (u.versionCode == skipCode) return
        val file = download(ctx, u)
        if (canInstallSilently(ctx)) {
            install(ctx, file, u)
        } else {
            val open = Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_INSTALL_UPDATE)
            notify(ctx, "Scout ${u.versionName} is ready", "Tap to install the update", PendingIntent.getActivity(ctx, 7, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
    }

    fun notify(ctx: Context, title: String, text: String, tap: PendingIntent) {
        val n = NotificationCompat.Builder(ctx, ScoutApp.UPDATES)
            .setSmallIcon(R.drawable.ic_stat_scout)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(4242, n) }
    }

    private fun formatDate(iso: String): String = runCatching {
        OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }.getOrDefault(iso)

    /** Release notes without Markdown, trimmed to a readable length. */
    private fun plainText(markdown: String): String {
        val text = markdown
            .replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
            .replace(Regex("(?m)^#+\\s*"), "")
            .replace(Regex("(?m)^[-*]\\s+"), "• ")
            .replace(Regex("[*_`]"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return if (text.length > 800) text.take(800).substringBeforeLast(' ') + "…" else text
    }
}
