package app.vestari.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.vestari.MainActivity
import app.vestari.R
import app.vestari.data.MailMessage
import app.vestari.data.Repository
import java.util.concurrent.TimeUnit

/** Фоновая проверка почты раз в 15 минут (минимум, который разрешает Android). */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repository.init(applicationContext)
        if (Repository.accounts.value.isEmpty()) return Result.success()
        val fresh = runCatching { Repository.sync() }.getOrElse { return Result.retry() }
        if (Repository.settings.value.notifications) Notifier.notify(applicationContext, fresh)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("mail-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

object Notifier {
    private const val CHANNEL = "new_mail"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Новые письма", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun notify(context: Context, messages: List<MailMessage>) {
        if (messages.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(context)
        messages.take(5).forEach { m ->
            val open = PendingIntent.getActivity(
                context, m.key.hashCode(),
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_MESSAGE_KEY, m.key)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(m.senderTitle)
                .setContentText(m.subject)
                .setStyle(NotificationCompat.BigTextStyle().bigText(m.subject + "\n" + m.preview))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setGroup("mail")
                .build()
            runCatching { nm.notify(m.key.hashCode(), n) }
        }
    }
}
