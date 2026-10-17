package com.ssjrocks.scout.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ssjrocks.scout.R
import com.ssjrocks.scout.ScoutApp
import com.ssjrocks.scout.data.Repo
import com.ssjrocks.scout.data.Settings
import com.ssjrocks.scout.net.Updater
import android.util.Log
import com.ssjrocks.scout.rank.Ranker
import com.ssjrocks.scout.ui.MainActivity
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as ScoutApp).repo
        runCatching { repo.refresh(waitForReddit = true) }.getOrElse { return Result.retry() }
        if (repo.settings.value.alertsEnabled) notifyMatches(applicationContext, repo)
        // Last, because a silent update install stops this process.
        val s = repo.settings.value
        if (s.autoUpdate) {
            runCatching { Updater.backgroundUpdate(applicationContext, s.skipUpdateCode) }
                .onFailure { Log.w("ScoutUpdate", "background update failed: $it") }
        }
        return Result.success()
    }

    companion object {
        private const val WORK = "refresh"

        fun schedule(context: Context, s: Settings) {
            val wm = WorkManager.getInstance(context)
            val prefs = context.getSharedPreferences("scout", Context.MODE_PRIVATE)
            if (s.refreshHours <= 0) {
                wm.cancelUniqueWork(WORK)
                prefs.edit().remove("scheduled").apply()
                return
            }
            // Re-registering a running job with UPDATE can cancel it mid-run (this runs at every
            // process start, including the one WorkManager starts for the job), so only update
            // when the schedule actually changed.
            val key = "${s.refreshHours}/${s.refreshWifiOnly}"
            val policy = if (prefs.getString("scheduled", null) == key) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.UPDATE
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (s.refreshWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(s.refreshHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, policy, req)
            prefs.edit().putString("scheduled", key).apply()
        }

        /**
         * Alerts for stories that arrived since the last check (in-app refreshes count as checked).
         * [test] sends the newest matching story regardless, to show what an alert looks like.
         * Returns how many notifications were posted.
         */
        fun notifyMatches(context: Context, repo: Repo, test: Boolean = false): Int {
            val since = if (test) 0L else repo.alertsCheckedAt()
            if (!test) repo.markAlertsChecked()
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return 0
            val s = repo.settings.value
            val words = s.alertWords.map { it.trim() }.filter { it.isNotEmpty() }
            if (words.isEmpty()) return 0
            val matcher = Ranker.mutedMatcher(words) ?: return 0
            val sources = repo.sourceMap(s)
            val filters = Ranker.Filters(s)
            val matches = repo.items.value.filter { it.fetched > since }.mapNotNull { item ->
                val src = sources[item.sourceId] ?: return@mapNotNull null
                val topics = Ranker.topicsOf(item, src)
                if (Ranker.blockReason(item, src, topics, Ranker.kindsOf(item, src), s, filters) != null) return@mapNotNull null
                val word = matcher.find(item.title)?.value ?: return@mapNotNull null
                Triple(item, src, word)
            }.sortedByDescending { it.first.published }.take(if (test) 1 else 5)

            val nm = NotificationManagerCompat.from(context)
            for ((item, src, word) in matches) {
                val open = Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_OPEN_STORY)
                    .setData(Uri.parse("scout://story/${item.id}"))
                    .putExtra(MainActivity.EXTRA_URL, item.link)
                    .putExtra(MainActivity.EXTRA_ID, item.id)
                val pi = PendingIntent.getActivity(context, item.id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val n = NotificationCompat.Builder(context, ScoutApp.ALERTS)
                    .setSmallIcon(R.drawable.ic_stat_scout)
                    .setContentTitle(item.title)
                    .setContentText("${item.publisher ?: src.name} · matches “$word”")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(item.title + "\n" + (item.publisher ?: src.name)))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setGroup("alerts")
                    .build()
                runCatching { nm.notify(item.id.hashCode(), n) }
            }
            return matches.size
        }
    }
}
