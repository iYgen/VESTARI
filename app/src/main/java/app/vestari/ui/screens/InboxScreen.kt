@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.vestari.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.vestari.R
import app.vestari.data.Categorizer
import app.vestari.data.Category
import app.vestari.data.ListStyle
import app.vestari.data.MailMessage
import app.vestari.data.Repository
import app.vestari.data.Smart
import app.vestari.data.SyncProgress
import app.vestari.ui.LocalNav
import app.vestari.ui.LocalToasts
import app.vestari.ui.Route
import app.vestari.ui.Tab
import app.vestari.ui.components.AccountAvatar
import app.vestari.ui.components.AccountSheet
import app.vestari.ui.components.FolderChip
import app.vestari.ui.components.FolderDialog
import app.vestari.ui.components.MessageCard
import app.vestari.ui.components.MoveToFolderSheet
import app.vestari.ui.components.bouncyClick
import app.vestari.ui.components.iconFor
import app.vestari.ui.components.spacing
import app.vestari.ui.components.tileColors
import app.vestari.ui.sharedCard
import app.vestari.ui.theme.AppTheme
import java.util.Locale

/** Разделитель в списке писем («Сегодня», «Вчера»…). */
private data class DateHeader(val title: String)

/** Особый фильтр «Важные» — письма, отмеченные флажком (звёздочкой) в любой почте, в том числе в Mail.ru. */
const val FLAGGED = "@flagged"

/** Размер заголовка экрана из настроек: 0 — маленький, 1 — средний, 2 — крупный. */
@Composable
fun titleStyle(size: Int): TextStyle = when (size) {
    2 -> MaterialTheme.typography.headlineLarge
    1 -> MaterialTheme.typography.headlineMedium
    else -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
}

/** Логотип Vestari из ресурсов иконки приложения. */
@Composable
fun AppLogo(size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f))) {
        Image(painterResource(R.drawable.ic_launcher_background), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Image(painterResource(R.drawable.ic_launcher_foreground), null, modifier = Modifier.fillMaxSize().scale(1.45f))
    }
}

/** Пока писем ещё нет — вместо пустого списка показываем ход загрузки. */
@Composable
private fun LoadingCard(p: SyncProgress) {
    Surface(shape = RoundedCornerShape(28.dp), color = AppTheme.extra.card, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val pulse = rememberInfiniteTransition(label = "logo")
            val s by pulse.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
            Box(Modifier.scale(s)) { AppLogo(72.dp) }
            Spacer(Modifier.height(18.dp))
            Text("Загружаю почту", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                if (p.total == 0) "Подключаюсь к серверу и читаю список папок…"
                else "Скачано ${fmt(p.loaded)} из ${fmt(p.total)} писем",
                color = AppTheme.extra.muted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            if (p.total == 0) LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)))
            else {
                val f by animateFloatAsState(p.fraction, label = "loadCard")
                LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Сначала появятся свежие письма, остальные докачаются в фоне и сами разложатся по папкам.",
                style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Тонкая плашка над списком, пока докачиваются старые письма. */
@Composable
private fun LoadingBanner(p: SyncProgress) {
    Surface(shape = RoundedCornerShape(18.dp), color = AppTheme.extra.accentSoft, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                if (p.total == 0) "Проверяю почту…" else "Загружаю письма: ${fmt(p.loaded)} из ${fmt(p.total)} · ${(p.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            val f by animateFloatAsState(p.fraction, label = "loadBanner")
            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)))
        }
    }
}

private fun fmt(n: Int): String = "%,d".format(Locale("ru"), n).replace(',', ' ')

/** Категория письма с учётом ручной раскладки. */
fun categoryOf(m: MailMessage, assignments: Map<String, String>): String? {
    val manual = assignments[m.key]
    return when {
        manual == null -> m.autoCategory
        manual.isEmpty() -> null
        else -> manual
    }
}

/** Сколько надо потянуть список вниз: до обновления и до панели папок. */
private val RefreshPull = 72.dp
private val PanelPull = 150.dp

@Composable
fun InboxScreen() {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val accounts by Repository.accounts.collectAsState()
    val settings by Repository.settings.collectAsState()
    val messagesMap by Repository.messages.collectAsState()
    val categories by Repository.categories.collectAsState()
    val assignments by Repository.assignments.collectAsState()
    val syncing by Repository.syncing.collectAsState()
    val progressMap by Repository.progress.collectAsState()

    var moveTarget by remember { mutableStateOf<MailMessage?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var autoSynced by rememberSaveable { mutableStateOf(false) }
    /** Выбранные письма (по ключу). Не пусто — режим выделения. */
    val selection = remember { mutableStateListOf<String>() }
    /** Письмо, у которого свайпом влево открыты кнопки «Удалить / В спам». */
    var actionKey by remember { mutableStateOf<String?>(null) }
    /** Поиск раскрыт в шапке: письма остаются на месте, а с текстом — фильтруются. */
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val selectedId = settings.selectedAccountId?.takeIf { id -> accounts.any { it.id == id } }
        ?: accounts.singleOrNull()?.id
    val selectedAccount = accounts.firstOrNull { it.id == selectedId }
    val all: List<MailMessage> = remember(messagesMap, selectedId) {
        if (selectedId == null) messagesMap.values.flatten().sortedByDescending { it.date }
        else messagesMap[selectedId].orEmpty().sortedByDescending { it.date }
    }
    val catById = categories.associateBy { it.id }
    val visible = remember(all, nav.folder, assignments, settings.cleanInbox) {
        when (nav.folder) {
            null -> if (!settings.cleanInbox) all else all.filter {
                val c = categoryOf(it, assignments)
                c != Categorizer.NEWSLETTERS && c != Categorizer.SALES
            }
            FLAGGED -> all.filter { it.flagged }
            else -> all.filter { categoryOf(it, assignments) == nav.folder }
        }
    }
    // Поиск по уже скачанным письмам текущей папки: отправитель, адрес, тема, начало текста.
    val shown = remember(visible, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) visible
        else visible.filter {
            it.senderTitle.lowercase().contains(q) || it.fromEmail.lowercase().contains(q) ||
                it.subject.lowercase().contains(q) || it.preview.lowercase().contains(q)
        }
    }
    // Непрочитанные по папкам: у папки появляется жирное название и «(N)».
    val unreadByCat = remember(all, assignments) {
        all.filter { !it.seen }.groupingBy { categoryOf(it, assignments) }.eachCount()
    }
    // Во «Все» письма выбранных папок сворачиваются в одну строку на месте самого свежего письма (как в Mail.ru).
    val bundles: Map<String, Bundle> = remember(shown, query, nav.folder, settings.bundleFolders, settings.bundled, categories, assignments) {
        if (!settings.bundleFolders || nav.folder != null || query.isNotBlank()) emptyMap()
        else shown.groupBy { categoryOf(it, assignments) }
            .filterKeys { it != null && it in settings.bundled }
            .mapNotNull { (id, list) -> catById[id]?.let { id!! to Bundle(it, list) } }
            .toMap()
    }
    // Строки списка: письма, свёрнутые папки и (если включено) разделители «Сегодня», «Вчера»…
    val rows: List<Any> = remember(shown, bundles, settings.groupByDate) {
        buildList {
            var last: String? = null
            val now = System.currentTimeMillis()
            val placed = mutableSetOf<String>()
            shown.forEach { m ->
                val cat = categoryOf(m, assignments)
                val row: Any = if (cat != null && cat in bundles) {
                    if (!placed.add(cat)) return@forEach
                    bundles.getValue(cat)
                } else m
                if (settings.groupByDate) {
                    val b = Smart.dateBucket(m.date, now)
                    if (b != last) { add(DateHeader(b)); last = b }
                }
                add(row)
            }
        }
    }
    val flaggedCount = remember(all) { all.count { it.flagged } }
    val selectedMsgs = remember(all, selection.toList()) { all.filter { it.key in selection } }
    // Прогресс загрузки: по выбранному ящику или суммарно по всем.
    val progress = remember(progressMap, selectedId) {
        val list = if (selectedId != null) listOfNotNull(progressMap[selectedId]) else progressMap.values.toList()
        if (list.none { it.loading }) null
        else SyncProgress(list.sumOf { it.loaded }, list.sumOf { it.total }, active = true)
    }
    val style = settings.listStyle
    val listState = rememberLazyListState()

    LaunchedEffect(accounts.size) {
        if (accounts.isNotEmpty() && !autoSynced) { autoSynced = true; Repository.refresh() }
    }
    // Письмо пропало (удалено, перенесено) — снимаем с него выделение.
    LaunchedEffect(all) { selection.retainAll(all.map { it.key }.toSet()) }

    BackHandler(searching && selection.isEmpty()) { searching = false; query = "" }
    BackHandler(selection.isNotEmpty()) { selection.clear() }
    BackHandler(actionKey != null && selection.isEmpty()) { actionKey = null }
    // Из папки («Госписьма», «Важные»…) «Назад» возвращает ко всем письмам, а не закрывает приложение.
    BackHandler(nav.folder != null && selection.isEmpty() && !searching && actionKey == null) { nav.folder = null }

    fun toggle(m: MailMessage) {
        actionKey = null
        if (m.key in selection) selection.remove(m.key) else selection.add(m.key)
    }

    fun selectAll(list: List<MailMessage>) {
        val keys = list.map { it.key }.filterNot { it in selection }
        selection.addAll(keys)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        toasts.show("Выбрано писем: ${selection.size}")
    }

    fun deleteAll(list: List<MailMessage>) {
        if (list.isEmpty()) return
        selection.clear(); actionKey = null
        Repository.hideAll(list)
        toasts.show(if (list.size == 1) "Письмо удалено" else "Удалено писем: ${list.size}", "Отменить",
            onAction = { Repository.unhideAll(list) },
            onTimeout = { list.forEach { Repository.commitDelete(it) } })
    }

    fun spamAll(list: List<MailMessage>) {
        if (list.isEmpty()) return
        selection.clear(); actionKey = null
        Repository.hideAll(list)
        toasts.show(if (list.size == 1) "Письмо в спаме" else "В спам: ${list.size}", "Отменить",
            onAction = { Repository.unhideAll(list) },
            onTimeout = { list.forEach { Repository.commitSpam(it) } })
    }

    // ---- жест «потянуть вниз»: немного — обновить почту, дальше — панель папок ----
    var pull by remember { mutableFloatStateOf(0f) }
    val refreshPx = with(density) { RefreshPull.toPx() }
    val panelPx = with(density) { PanelPull.toPx() }
    val onRelease by rememberUpdatedState { p: Float ->
        when {
            p >= panelPx -> nav.folderPanelOpen = true
            p >= refreshPx -> Repository.refresh(selectedId)
        }
    }
    val pullConnection = remember(refreshPx, panelPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Список «оттянут» и палец идёт вверх — сначала убираем оттяжку.
                if (available.y < 0 && pull > 0f) {
                    val used = maxOf(available.y, -pull)
                    pull += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                val before = pull
                pull = (pull + available.y * 0.5f).coerceAtMost(panelPx * 1.35f)
                if ((before < refreshPx && pull >= refreshPx) || (before < panelPx && pull >= panelPx)) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                val p = pull
                onRelease(p)
                animate(p, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> pull = v }
                return available
            }
        }
    }

    Box(Modifier.fillMaxSize().nestedScroll(pullConnection)) {
        PullHint(pull, refreshPx, panelPx, Modifier.align(Alignment.TopCenter).statusBarsPadding())

        LazyColumn(
            Modifier.fillMaxSize().graphicsLayer { translationY = pull },
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(style.spacing()),
        ) {
            item(key = "header") {
                Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                    Header(
                        avatar = { AccountAvatar(selectedAccount, 42.dp) },
                        title = when (nav.folder) {
                            null -> "Входящие"
                            FLAGGED -> "Важные"
                            else -> catById[nav.folder]?.name ?: "Входящие"
                        },
                        titleSize = settings.titleSize,
                        syncing = syncing,
                        onAccounts = { nav.accountSheetOpen = true },
                        onSearch = { searching = true },
                        onCompose = { nav.push(Route.Compose()) },
                        onBack = if (nav.folder != null) ({ nav.folder = null }) else null,
                    )
                    if (accounts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            AccountPill(selectedAccount?.email ?: "Все ящики") { nav.accountSheetOpen = true }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Папки — одной строкой, листаются вбок. Долгое нажатие — выделить все письма папки.
            item(key = "chips") {
                val dark = AppTheme.extra.isDark
                LazyRow(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item(key = "panel") { RoundChip(Icons.Outlined.FolderOpen, "Все папки ящика") { nav.folderPanelOpen = true } }
                    item(key = "all") {
                        FolderChip(
                            "Все", nav.folder == null, badge = all.count { !it.seen }, icon = Icons.Outlined.AllInbox,
                            onLongClick = { selectAll(visible) },
                        ) { nav.folder = null }
                    }
                    if (flaggedCount > 0) item(key = FLAGGED) {
                        FolderChip(
                            "Важные", nav.folder == FLAGGED, badge = all.count { it.flagged && !it.seen },
                            icon = Icons.Outlined.StarOutline, iconTint = Color(0xFFF5B400),
                            onLongClick = { selectAll(all.filter { it.flagged }) },
                        ) { nav.folder = if (nav.folder == FLAGGED) null else FLAGGED }
                    }
                    items(categories, key = { it.id }) { c ->
                        FolderChip(
                            c.name, nav.folder == c.id, badge = unreadByCat[c.id] ?: 0,
                            icon = iconFor(c.icon), iconTint = tileColors(c.id, dark).second,
                            onLongClick = { selectAll(all.filter { categoryOf(it, assignments) == c.id }) },
                        ) { nav.folder = if (nav.folder == c.id) null else c.id }
                    }
                    item(key = "add") { RoundChip(Icons.Rounded.Add, "Новая папка") { creatingFolder = true } }
                }
            }

            if (progress != null && all.isNotEmpty()) {
                item(key = "progress") { LoadingBanner(progress) }
            }

            if (accounts.isEmpty()) {
                item(key = "empty-accounts") { NoAccountsCard { nav.push(Route.AddAccount) } }
            } else if (all.isEmpty() && (progress != null || syncing)) {
                item(key = "loading") { LoadingCard(progress ?: SyncProgress(active = true)) }
            } else if (shown.isEmpty() && (!syncing || query.isNotBlank())) {
                item(key = "empty") {
                    Text(
                        when {
                            query.isNotBlank() -> "Ничего не найдено по «${query.trim()}»"
                            nav.folder == null -> "Писем пока нет"
                            nav.folder == FLAGGED -> "Нет писем, отмеченных флажком"
                            else -> "В этой папке пусто"
                        },
                        color = AppTheme.extra.muted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }
            }

            items(
                rows,
                key = { r ->
                    when (r) {
                        is MailMessage -> r.key
                        is Bundle -> "b_" + r.category.id
                        else -> "h_" + (r as DateHeader).title
                    }
                },
                contentType = { r -> when (r) { is MailMessage -> 0; is Bundle -> 2; else -> 1 } },
            ) { r ->
                when (r) {
                    is DateHeader -> Text(
                        r.title, style = MaterialTheme.typography.labelLarge, color = AppTheme.extra.muted,
                        modifier = Modifier.animateItem().padding(start = 8.dp, top = 10.dp, bottom = 2.dp),
                    )
                    is Bundle -> BundleRow(r, Modifier.animateItem()) { nav.folder = r.category.id }
                    is MailMessage -> {
                        val m = r
                        if (actionKey == m.key && selection.isEmpty()) {
                            SwipeActions(
                                style = style, modifier = Modifier.animateItem(),
                                onSpam = { spamAll(listOf(m)) },
                                onDelete = { deleteAll(listOf(m)) },
                                onCancel = { actionKey = null },
                            )
                        } else SwipeableMessage(
                            m = m,
                            style = style,
                            showPreview = settings.showPreview,
                            otp = if (settings.otpCodes && Smart.isFresh(m)) Smart.otp(m) else null,
                            category = categoryOf(m, assignments)?.let { catById[it] },
                            showAccount = if (selectedId == null && accounts.size > 1) accounts.firstOrNull { it.id == m.accountId }?.email else null,
                            selected = m.key in selection,
                            swipeEnabled = selection.isEmpty(),
                            modifier = Modifier.animateItem(),
                            onOpen = { if (selection.isNotEmpty()) toggle(m) else { actionKey = null; nav.push(Route.Message(m.key)) } },
                            onSelect = { toggle(m) },
                            onMove = { if (selection.isNotEmpty()) toggle(m) else moveTarget = m },
                            onReveal = { actionKey = m.key },
                        )
                    }
                }
            }
        }

        // Поиск: пока ничего не набрано, список затемнён, но виден; нажатие на затемнение закрывает поиск.
        AnimatedVisibility(searching && query.isBlank(), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (AppTheme.extra.isDark) 0.45f else 0.32f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { searching = false },
            )
        }
        AnimatedVisibility(
            searching && selection.isEmpty(),
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(120)), exit = fadeOut(tween(120)),
        ) {
            InlineSearchBar(
                query = query,
                found = if (query.isBlank()) null else shown.size,
                onQuery = { query = it },
                onClose = { searching = false; query = "" },
            )
        }

        // Режим выделения: панель действий наверху поверх списка.
        AnimatedVisibility(
            selection.isNotEmpty(),
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(),
        ) {
            SelectionBar(
                count = selectedMsgs.size,
                allRead = selectedMsgs.all { it.seen },
                onClose = { selection.clear() },
                onSelectAll = { selectAll(visible) },
                onToggleRead = {
                    val markRead = !selectedMsgs.all { it.seen }
                    Repository.setSeenAll(selectedMsgs, markRead)
                    toasts.show(if (markRead) "Отмечено прочитанными" else "Отмечено непрочитанными")
                    selection.clear()
                },
                onSpam = { spamAll(selectedMsgs) },
                onDelete = { deleteAll(selectedMsgs) },
            )
        }

        if (selection.isEmpty()) {
            ComposeFab(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 100.dp)) {
                nav.push(Route.Compose())
            }
        }
    }

    if (nav.accountSheetOpen) AccountSheet { nav.accountSheetOpen = false }
    if (nav.folderPanelOpen) FolderPanel { nav.folderPanelOpen = false }
    moveTarget?.let { MoveToFolderSheet(it) { moveTarget = null } }
    if (creatingFolder) {
        FolderDialog("Новая папка", onDismiss = { creatingFolder = false }) { name, icon ->
            creatingFolder = false
            val c = Repository.addCategory(name, icon)
            toasts.show("Папка создана", "Отменить", onAction = { Repository.removeCategory(c.id) })
        }
    }
}

/** Подсказка над оттянутым списком: «обновить» → «откроются папки». */
@Composable
private fun PullHint(pull: Float, refreshPx: Float, panelPx: Float, modifier: Modifier) {
    if (pull <= 1f) return
    val stage = when {
        pull >= panelPx -> 2
        pull >= refreshPx -> 1
        else -> 0
    }
    Column(
        modifier.padding(top = 10.dp).alpha((pull / refreshPx).coerceIn(0f, 1f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (stage == 2) Icons.Outlined.FolderOpen else Icons.Rounded.Refresh, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = if (stage == 2) 0f else pull / refreshPx * 270f },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when (stage) {
                2 -> "Отпустите — откроются папки"
                1 -> "Отпустите — обновить · тяните дальше — папки"
                else -> "Потяните, чтобы обновить"
            },
            style = MaterialTheme.typography.labelMedium, color = AppTheme.extra.muted,
        )
    }
}

@Composable
private fun Header(
    avatar: @Composable () -> Unit,
    title: String,
    titleSize: Int,
    syncing: Boolean,
    onAccounts: () -> Unit,
    onSearch: () -> Unit,
    onCompose: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val showLogo = app.vestari.ui.components.LocalLook.current.showLogo
    // Длинное название или идёт проверка почты — заголовок чуть меньше, чтобы не обрезался.
    val base = titleStyle(titleSize)
    val style = if (syncing || title.length > 9 || onBack != null) base.copy(fontSize = base.fontSize * 0.82f, lineHeight = base.lineHeight * 0.82f) else base
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-8).dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", modifier = Modifier.size(26.dp))
            }
        }
        if (!showLogo) {
            // Без логотипа ящик выбирается по аватару; с логотипом — нажатием на сам логотип.
            Row(
                Modifier.clip(RoundedCornerShape(50)).bouncyClick(onClick = onAccounts).padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                avatar()
                Icon(Icons.Rounded.KeyboardArrowDown, "Аккаунты", tint = AppTheme.extra.muted)
            }
            Spacer(Modifier.width(10.dp))
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            app.vestari.ui.components.BrandTitle(
                title, style, modifier = Modifier.weight(1f, fill = false), onLogoClick = onAccounts,
            )
            // Маленький индикатор проверки почты вместо крутилки на весь экран.
            AnimatedVisibility(syncing, enter = fadeIn(), exit = fadeOut()) {
                CircularProgressIndicator(Modifier.padding(start = 10.dp).size(16.dp), strokeWidth = 2.dp)
            }
        }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Поиск", modifier = Modifier.size(26.dp)) }
        IconButton(onClick = onCompose) {
            Icon(Icons.Outlined.EditNote, "Написать", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
    }
}

/** Строка поиска, раскрывающаяся от лупы на всю ширину шапки. */
@Composable
private fun InlineSearchBar(query: String, found: Int?, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { expanded = true; delay(180); runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            expanded,
            enter = expandHorizontally(tween(220), expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(tween(160), shrinkTowards = Alignment.End) + fadeOut(),
        ) {
            Surface(shape = RoundedCornerShape(50), color = AppTheme.extra.card, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.height(54.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Поиск по письмам", style = MaterialTheme.typography.bodyLarge, color = AppTheme.extra.muted)
                            inner()
                        },
                    )
                    if (found != null) Text("$found", style = MaterialTheme.typography.labelMedium, color = AppTheme.extra.muted, modifier = Modifier.padding(horizontal = 6.dp))
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Закрыть поиск") }
                }
            }
        }
    }
}

/**
 * Панель режима выделения. Точка — отметить выбранные непрочитанными (если все прочитаны),
 * иначе открытый конверт — отметить прочитанными. Рядом «В спам» и «Удалить».
 */
@Composable
private fun SelectionBar(
    count: Int,
    allRead: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleRead: () -> Unit,
    onSpam: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = AppTheme.extra.card, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Снять выделение") }
            Text("$count", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
            IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, "Выбрать все письма папки") }
            IconButton(onClick = onToggleRead) {
                if (allRead) {
                    // Точка: отметить все выбранные непрочитанными.
                    Box(Modifier.size(12.dp).clip(CircleShape).background(AppTheme.extra.unreadDot))
                } else Icon(Icons.Outlined.MarkEmailRead, "Отметить прочитанными")
            }
            IconButton(onClick = onSpam) { Icon(Icons.Outlined.Report, "В спам") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Удалить", tint = AppTheme.extra.unreadDot) }
        }
    }
}

@Composable
private fun AccountPill(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(AppTheme.extra.accentSoft)
            .bouncyClick(onClick = onClick)
            .padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RoundChip(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(AppTheme.extra.card.copy(alpha = 0.85f))
            .bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
}

/** Кнопки, открывшиеся свайпом влево: «В спам», «Удалить», отмена. */
@Composable
private fun SwipeActions(style: ListStyle, modifier: Modifier, onSpam: () -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    val h = when (style) { ListStyle.Cards -> 76.dp; ListStyle.Compact -> 64.dp; ListStyle.List -> 52.dp }
    Row(
        modifier.fillMaxWidth().height(h),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionPill(Icons.Outlined.Report, "В спам", Color(0xFFE08A1E), Modifier.weight(1f), onSpam)
        ActionPill(Icons.Outlined.Delete, "Удалить", AppTheme.extra.unreadDot, Modifier.weight(1f), onDelete)
        Box(
            Modifier.size(h).clip(RoundedCornerShape(18.dp)).background(AppTheme.extra.card).bouncyClick(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, "Отмена", tint = AppTheme.extra.muted) }
    }
}

@Composable
private fun ActionPill(icon: ImageVector, text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(color).bouncyClick(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Письмо со свайпами: слева направо — прочитано ↔ непрочитано (письмо остаётся на месте),
 * справа налево — открываются кнопки «Удалить» и «В спам».
 */
@Composable
private fun SwipeableMessage(
    m: MailMessage,
    style: ListStyle,
    showPreview: Boolean,
    otp: String?,
    category: Category?,
    showAccount: String?,
    selected: Boolean,
    swipeEnabled: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onMove: () -> Unit,
    onReveal: () -> Unit,
) {
    val current by rememberUpdatedState(m)
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.3f },
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> { Repository.setSeen(current, !current.seen); false }
                SwipeToDismissBoxValue.EndToStart -> { onReveal(); false }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = swipeEnabled,
        enableDismissFromEndToStart = swipeEnabled,
        backgroundContent = {
            val dir = state.dismissDirection
            val toStart = dir == SwipeToDismissBoxValue.EndToStart
            val color by animateColorAsState(
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                    SwipeToDismissBoxValue.EndToStart -> AppTheme.extra.unreadDot
                    else -> Color.Transparent
                }, label = "swipeBg",
            )
            val iconScale by animateFloatAsState(if (state.progress > 0.3f) 1.15f else 0.9f, label = "swipeIcon")
            Row(
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(if (style == ListStyle.Cards) 24.dp else 16.dp))
                    .background(color).padding(horizontal = 24.dp)
                    .alpha(if (dir == SwipeToDismissBoxValue.Settled) 0f else 1f),
                horizontalArrangement = if (toStart) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (icon, label) = when {
                    toStart -> Icons.Outlined.Delete to "Удалить или в спам"
                    m.seen -> Icons.Outlined.MarkEmailUnread to "Непрочитанное"
                    else -> Icons.Outlined.MarkEmailRead to "Прочитано"
                }
                if (toStart) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(10.dp))
                }
                Icon(icon, null, tint = Color.White, modifier = Modifier.scale(iconScale))
                if (!toStart) {
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) {
        MessageCard(
            m = m,
            category = category,
            showAccount = showAccount,
            style = style,
            showPreview = showPreview,
            otp = otp,
            selected = selected,
            onTileClick = onSelect,
            modifier = Modifier.sharedCard(m.key),
            onClick = onOpen,
            onLongClick = onMove,
            onStar = { Repository.setFlagged(m, !m.flagged) },
        )
    }
}

@Composable
private fun ComposeFab(modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(66.dp)
            .bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, shadowElevation = 10.dp, color = Color.Transparent, modifier = Modifier.size(66.dp)) {
            Box(
                Modifier.background(Brush.linearGradient(listOf(primary.copy(alpha = 0.8f), primary))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Edit, "Написать", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun NoAccountsCard(onAdd: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = AppTheme.extra.card, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(72.dp)
            Spacer(Modifier.height(16.dp))
            Text("Добавьте свою почту", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Mail.ru, Яндекс, Gmail или любой ящик с IMAP. Можно добавить сколько угодно и переключаться между ними.",
                textAlign = TextAlign.Center, color = AppTheme.extra.muted,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAdd, shape = RoundedCornerShape(18.dp)) {
                Text("Добавить ящик", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
    }
}
