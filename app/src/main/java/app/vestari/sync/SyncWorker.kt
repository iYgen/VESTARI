package app.vestari.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.vestari.data.Repository
import java.util.concurrent.TimeUnit

/**
 * Фоновая проверка почты раз в 15 минут (минимум, который разрешает Android).
 * Уведомления о новых письмах показывает [Notifier] — он подписан на результат проверки.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repository.init(applicationContext)
        Notifier.attach(applicationContext)
        if (Repository.accounts.value.isEmpty()) return Result.success()
        runCatching { Repository.sync() }.getOrElse { return Result.retry() }
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
