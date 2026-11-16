package app.vestari.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.vestari.R
import app.vestari.data.Repository
import java.util.concurrent.TimeUnit

/** Отправляет запланированное письмо (черновик с временем отправки), когда подойдёт время и будет сеть. */
class ScheduledSendWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repository.init(applicationContext)
        val id = inputData.getString(KEY) ?: return Result.success()
        val d = Repository.draft(id) ?: return Result.success() // письмо удалили или уже отправили
        if (d.sendAt == null) return Result.success() // отправку отменили
        return runCatching { Repository.sendDraft(d) }.fold(
            onSuccess = {
                notify(applicationContext, "Отложенное письмо отправлено", d.subject.ifBlank { "(без темы)" })
                Result.success()
            },
            onFailure = { e ->
                if (runAttemptCount < 5) Result.retry()
                else {
                    notify(applicationContext, "Не удалось отправить отложенное письмо", e.message ?: d.subject)
                    Result.failure()
                }
            },
        )
    }

    companion object {
        private const val KEY = "draftId"
        private const val CHANNEL = "outbox"

        fun schedule(context: Context, draftId: String, at: Long) {
            val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)
            val req = OneTimeWorkRequestBuilder<ScheduledSendWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY to draftId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("send-$draftId", ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(context: Context, draftId: String) {
            WorkManager.getInstance(context).cancelUniqueWork("send-$draftId")
        }

        private fun notify(context: Context, title: String, text: String) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Отправка писем", NotificationManager.IMPORTANCE_LOW))
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(title.hashCode(), n) }
        }
    }
}
