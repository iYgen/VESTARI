package app.vestari.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.vestari.MainActivity
import app.vestari.R
import app.vestari.data.MailMessage
import app.vestari.data.Repository
import app.vestari.data.Smart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Уведомления о новых письмах — отдельной группой на каждый ящик:
 *
 *     [значок] Vestari · me@mail.ru  ˅
 *        письмо 1
 *        письмо 2 …
 *
 * В группе не больше [app.vestari.data.Settings.notifyMax] писем. Каждое письмо смахивается отдельно
 * (только скрыть или отметить прочитанным — по настройке), у каждого есть кнопка «Прочитано».
 * Смахнули всю группу — она не появится, пока не придёт новое письмо. Прочитали письмо в приложении
 * или в самой почте — оно само исчезает из шторки. Число непрочитанных уходит на значок приложения.
 */
object Notifier {
    private const val CHANNEL = "new_mail"
    private const val PREFS = "mail_notify"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var attached = false

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Новые письма", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(true) },
        )
        ProgressNotice.createChannel(context)
    }

    /** Подключиться к хранилищу один раз на процесс: новые письма → шторка, изменения писем → обновить шторку. */
    fun attach(context: Context) {
        if (attached) return
        synchronized(this) {
            if (attached) return
            attached = true
        }
        val app = context.applicationContext
        createChannel(app)
        Repository.newMailListener = { fresh -> onNewMail(app, fresh) }
        // Письмо прочитали (здесь или в самой почте), удалили, перенесли — пересобираем шторку без звука.
        scope.launch {
            combine(Repository.messages, Repository.settings) { _, s -> s.notifications }.collectLatest {
                delay(800)
                render(app, alert = emptySet())
            }
        }
        scope.launch { ProgressNotice.observe(app) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun shownKey(accountId: String) = "shown_$accountId"

    /** Новые письма после проверки почты: добавляем их в группу ящика и звеним. */
    fun onNewMail(context: Context, fresh: List<MailMessage>) {
        if (!Repository.settings.value.notifications) return
        val important = fresh.filter { !it.seen && !Repository.isQuiet(Repository.categoryOf(it)) }
        if (important.isEmpty()) return
        val p = prefs(context)
        val hidden = p.getStringSet("hidden", emptySet()).orEmpty()
        val edit = p.edit()
        important.groupBy { it.accountId }.forEach { (acc, list) ->
            val shown = p.getStringSet(shownKey(acc), emptySet()).orEmpty() + list.map { it.key }.filter { it !in hidden }
            edit.putStringSet(shownKey(acc), shown)
        }
        edit.apply()
        render(context, alert = important.map { it.accountId }.toSet())
    }

    /** Собрать шторку заново по текущему состоянию писем. [alert] — ящики, по которым нужно звенеть. */
    @Synchronized
    fun render(context: Context, alert: Set<String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val settings = Repository.settings.value
        val p = prefs(context)
        val hidden = p.getStringSet("hidden", emptySet()).orEmpty()
        val edit = p.edit()
        val all = Repository.messages.value
        Repository.accounts.value.forEach { acc ->
            val group = "acc_${acc.id}"
            val byKey = all[acc.id].orEmpty().associateBy { it.key }
            val shownKeys = p.getStringSet(shownKey(acc.id), emptySet()).orEmpty()
            // Остаются только непрочитанные и не скрытые письма; прочитанные выпадают сами.
            val alive = shownKeys.mapNotNull { byKey[it] }.filter { !it.seen && it.key !in hidden }
            if (alive.size != shownKeys.size) edit.putStringSet(shownKey(acc.id), alive.map { it.key }.toSet())
            val visible = if (settings.notifications) alive.sortedByDescending { it.date }.take(settings.notifyMax.coerceIn(1, 5)) else emptyList()

            // Уведомления этой группы, которых больше не должно быть, снимаем.
            val wanted = visible.map { childId(it.key) }.toSet()
            nm.activeNotifications.filter { it.notification.group == group && it.id != summaryId(acc.id) && it.id !in wanted }
                .forEach { nm.cancel(it.id) }
            if (visible.isEmpty()) { nm.cancel(summaryId(acc.id)); return@forEach }

            // Число на значке приложения — все непрочитанные «важные» письма ящика (без тихих рассылок).
            val unread = all[acc.id].orEmpty().count { !it.seen && !Repository.isQuiet(Repository.categoryOf(it)) }
            val loud = acc.id in alert
            visible.forEach { m -> runCatching { nm.notify(childId(m.key), child(context, m, acc.email, group)) } }
            val summary = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setSubText(acc.email)
                .setContentTitle(if (alive.size == 1) visible.first().senderTitle else "Новых писем: ${alive.size}")
                .setContentText(visible.joinToString(", ") { it.senderTitle })
                .setStyle(
                    NotificationCompat.InboxStyle().also { s ->
                        visible.forEach { s.addLine("${it.senderTitle}  ${it.subject}") }
                        s.setSummaryText(acc.email)
                    },
                )
                .setGroup(group)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setOnlyAlertOnce(!loud)
                .setNumber(unread)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setContentIntent(openApp(context, acc.id))
                .setDeleteIntent(action(context, ACTION_DISMISS_GROUP, acc.id, summaryId(acc.id)))
                .addAction(0, "Прочитать все", action(context, ACTION_READ_ALL, acc.id, summaryId(acc.id) + 1))
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(summaryId(acc.id), summary) }
        }
        edit.apply()
    }

    private fun child(context: Context, m: MailMessage, email: String, group: String): Notification {
        val code = if (Repository.settings.value.otpCodes) Smart.otp(m) else null
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setSubText(email)
            .setContentTitle(if (code != null) "Код $code · ${m.senderTitle}" else m.senderTitle)
            .setContentText(m.subject)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (m.preview.isBlank()) m.subject else m.subject + "\n" + m.preview))
            .setWhen(m.date)
            .setShowWhen(true)
            .setGroup(group)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setOnlyAlertOnce(true)
            .setNumber(0)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setContentIntent(openMessage(context, m.key))
            .setDeleteIntent(action(context, ACTION_DISMISS, m.key, childId(m.key) + 1))
            .addAction(0, "Прочитано", action(context, ACTION_READ, m.key, childId(m.key) + 2))
            .setAutoCancel(true)
            .build()
    }

    private fun childId(key: String) = key.hashCode()
    private fun summaryId(accountId: String) = ("summary_$accountId").hashCode()

    private fun openMessage(context: Context, key: String) = PendingIntent.getActivity(
        context, key.hashCode(),
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_MESSAGE_KEY, key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context, accountId: String) = PendingIntent.getActivity(
        context, ("open_$accountId").hashCode(),
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, what: String, arg: String, code: Int) = PendingIntent.getBroadcast(
        context, code,
        Intent(context, NotificationActionReceiver::class.java).setAction(what).putExtra(EXTRA_ARG, arg),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // ---- действия из шторки ----

    internal const val ACTION_READ = "app.vestari.READ"
    internal const val ACTION_READ_ALL = "app.vestari.READ_ALL"
    internal const val ACTION_DISMISS = "app.vestari.DISMISS"
    internal const val ACTION_DISMISS_GROUP = "app.vestari.DISMISS_GROUP"
    internal const val ACTION_PAUSE_LOADING = "app.vestari.PAUSE_LOADING"
    internal const val EXTRA_ARG = "arg"

    /** Письмо больше не показывать в шторке (до новых писем). */
    internal fun hide(context: Context, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val p = prefs(context)
        // Список скрытых не растёт бесконечно: храним последние 2000.
        val hidden = (p.getStringSet("hidden", emptySet()).orEmpty() + keys).toList().takeLast(2000).toSet()
        p.edit().putStringSet("hidden", hidden).apply()
    }

    internal fun shownFor(context: Context, accountId: String): Set<String> =
        prefs(context).getStringSet(shownKey(accountId), emptySet()).orEmpty()

    internal fun clearShown(context: Context, accountId: String) {
        prefs(context).edit().remove(shownKey(accountId)).apply()
    }
}

/** Кнопки и смахивания в шторке: «Прочитано», «Прочитать все», смахнуть письмо или всю группу, пауза загрузки. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val arg = intent.getStringExtra(Notifier.EXTRA_ARG) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Repository.init(context)
                Notifier.attach(context)
                when (intent.action) {
                    Notifier.ACTION_READ -> {
                        Notifier.hide(context, listOf(arg))
                        Notifier.render(context, emptySet())
                        Repository.markSeenNow(arg)
                    }
                    Notifier.ACTION_READ_ALL -> {
                        val keys = Notifier.shownFor(context, arg)
                        Notifier.hide(context, keys)
                        Notifier.clearShown(context, arg)
                        Notifier.render(context, emptySet())
                        val msgs = Repository.messages.value[arg].orEmpty().filter { it.key in keys }
                        Repository.setSeenAll(msgs, true)
                        delay(4000) // дать отметке уйти на сервер, пока процесс жив
                    }
                    Notifier.ACTION_DISMISS -> {
                        Notifier.hide(context, listOf(arg))
                        if (Repository.settings.value.notifySwipeMarksRead) Repository.markSeenNow(arg)
                        // Чуть ждём: если смахнули всю группу, сначала обработается она, и шторка не мигнёт.
                        delay(700)
                        Notifier.render(context, emptySet())
                    }
                    Notifier.ACTION_DISMISS_GROUP -> {
                        val keys = Notifier.shownFor(context, arg)
                        Notifier.hide(context, keys)
                        Notifier.clearShown(context, arg)
                        if (Repository.settings.value.notifySwipeMarksRead) {
                            Repository.setSeenAll(Repository.messages.value[arg].orEmpty().filter { it.key in keys }, true)
                            delay(4000)
                        }
                        Notifier.render(context, emptySet())
                    }
                    Notifier.ACTION_PAUSE_LOADING -> Repository.setLoadingPaused(true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Пустой Bundle с флагами «живого» уведомления Android 16 (значок с ходом загрузки в строке состояния). */
internal fun promotedExtras(shortText: String): Bundle = Bundle().apply {
    putBoolean("android.requestPromotedOngoing", true)
    putString("android.shortCriticalText", shortText)
}
