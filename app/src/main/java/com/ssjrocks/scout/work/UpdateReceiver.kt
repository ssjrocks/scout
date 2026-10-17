package com.ssjrocks.scout.work

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.ssjrocks.scout.BuildConfig
import com.ssjrocks.scout.net.Updater
import com.ssjrocks.scout.ui.MainActivity

/** Result of an update install session (see [Updater.install]). */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val version = intent.getStringExtra(EXTRA_VERSION) ?: "update"
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android wants a confirmation (first update, or not allowed to install apps yet).
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Updater.foreground && runCatching { ctx.startActivity(confirm) }.isSuccess) return
                val tap = PendingIntent.getActivity(ctx, 8, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                Updater.notify(ctx, "Scout $version is ready", "Tap to install the update", tap)
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("ScoutUpdate", "installed $version")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                Log.w("ScoutUpdate", "install of $version failed: $msg")
                Updater.results.tryEmit(status to msg)
                val prefs = ctx.getSharedPreferences("scout", Context.MODE_PRIVATE).edit().remove("installing_code")
                // Cancelling Android's dialog isn't an error; other failures are shown next launch.
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED && !Updater.foreground) prefs.putString("update_error", msg)
                prefs.apply()
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED && !Updater.foreground) {
                    val open = Intent(ctx, MainActivity::class.java)
                    Updater.notify(
                        ctx, "Scout $version didn't install", msg,
                        PendingIntent.getActivity(ctx, 9, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_STATUS = "com.ssjrocks.scout.UPDATE_STATUS"
        const val EXTRA_VERSION = "version"
    }
}

/** After Android has replaced the app with an update Scout installed, say so. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = ctx.getSharedPreferences("scout", Context.MODE_PRIVATE)
        val expected = prefs.getInt("installing_code", 0)
        if (expected == 0 || expected != BuildConfig.VERSION_CODE) return
        prefs.edit().remove("installing_code").remove("update_error").apply()
        val open = Intent(ctx, MainActivity::class.java)
        Updater.notify(
            ctx, "Scout updated to ${BuildConfig.VERSION_NAME}", "Tap to open",
            PendingIntent.getActivity(ctx, 10, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }
}
