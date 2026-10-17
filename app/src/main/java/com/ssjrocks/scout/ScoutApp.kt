package com.ssjrocks.scout

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.ssjrocks.scout.data.Repo
import com.ssjrocks.scout.net.Http
import com.ssjrocks.scout.work.RefreshWorker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ScoutApp : Application(), ImageLoaderFactory {
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        repo = Repo(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ALERTS, "Keyword alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New stories that match your alert words"
            },
        )
        // Keep the background refresh schedule in step with the settings.
        repo.scope.launch {
            repo.settings.map { it.refreshHours to it.refreshWifiOnly }.distinctUntilChanged().collect {
                RefreshWorker.schedule(this@ScoutApp, repo.settings.value)
            }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Http.client)
        .crossfade(true)
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(250L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .build()

    companion object {
        const val ALERTS = "alerts"
    }
}
