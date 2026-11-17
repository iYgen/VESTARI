package app.vestari.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.vestari.MainActivity
import app.vestari.R
import app.vestari.data.Repository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate

/**
 * Ход загрузки писем в шторке. На Android 16 уведомление «продвигается» в строку состояния —
 * рядом с часами виден короткий текст «27%», как у такси или таймера.
 * Пока идёт загрузка, приложение держит сервис переднего плана, чтобы Android не усыпил скачивание.
 */
object ProgressNotice {
    private const val CHANNEL = "sync_progress"
    internal const val ID = 7001
    @Volatile private var shown = false

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Загрузка писем", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                description = "Ход скачивания писем с сервера"
            },
        )
    }

    suspend fun observe(context: Context) {
        combine(Repository.progress, Repository.settings) { map, s ->
            val loading = map.values.filter { it.loading }
            if (!s.syncProgressNotice || loading.isEmpty()) null
            else loading.sumOf { it.loaded } to loading.sumOf { it.total }
        }.conflate().collect { state ->
            if (state == null) hide(context) else show(context, state.first, state.second)
            delay(1000) // не чаще раза в секунду — система всё равно режет частые обновления
        }
    }

    private fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun show(context: Context, loaded: Int, total: Int) {
        if (!allowed(context)) return
        val n = build(context, loaded, total)
        if (!shown) {
            shown = true
            // Сервис переднего плана держит процесс живым. Если Android не даёт его запустить из фона —
            // просто показываем обычное постоянное уведомление.
            val ok = runCatching { ContextCompat.startForegroundService(context, Intent(context, SyncProgressService::class.java)) }.isSuccess
            if (!ok) runCatching { context.getSystemService(NotificationManager::class.java).notify(ID, n) }
        } else {
            runCatching { context.getSystemService(NotificationManager::class.java).notify(ID, n) }
        }
    }

    private fun hide(context: Context) {
        if (!shown) return
        shown = false
        runCatching { context.stopService(Intent(context, SyncProgressService::class.java)) }
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    fun build(context: Context, loaded: Int, total: Int): Notification {
        val pct = if (total > 0) (loaded * 100L / total).toInt().coerceIn(0, 100) else 0
        val open = PendingIntent.getActivity(
            context, ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pause = PendingIntent.getBroadcast(
            context, ID + 1,
            Intent(context, NotificationActionReceiver::class.java).setAction(Notifier.ACTION_PAUSE_LOADING).putExtra(Notifier.EXTRA_ARG, "all"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Загрузка писем · $pct%")
            .setContentText(if (total > 0) "%,d из %,d".format(loaded, total).replace(',', ' ') else "Считаю письма…")
            .setProgress(100, pct, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "Пауза", pause)
            .addExtras(promotedExtras("$pct%"))
            .build()
    }
}

/** Сервис переднего плана на время загрузки: без него Android может остановить скачивание в фоне. */
class SyncProgressService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = Repository.progress.value.values.filter { it.loading }
        val n = ProgressNotice.build(this, p.sumOf { it.loaded }, p.sumOf { it.total })
        val started = runCatching {
            ServiceCompat.startForeground(
                this, ProgressNotice.ID, n,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        }.isSuccess
        if (!started) {
            runCatching { getSystemService(NotificationManager::class.java).notify(ProgressNotice.ID, n) }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Android 15+: лимит времени для dataSync исчерпан — уходим с переднего плана, загрузка продолжится позже. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }
}
