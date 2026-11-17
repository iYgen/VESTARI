package app.vestari.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vestari.data.SyncBatchOptions
import app.vestari.data.SyncLimitOptions
import app.vestari.data.Account
import app.vestari.data.Category
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import app.vestari.data.categoryTree
import app.vestari.data.childrenOf
import app.vestari.data.subtreeIds
import app.vestari.data.pathTo
import app.vestari.data.isTopLevel
import app.vestari.data.Providers
import app.vestari.data.Repository
import app.vestari.ui.LocalNav
import app.vestari.ui.LocalToasts
import app.vestari.ui.Route
import app.vestari.ui.Tab
import app.vestari.ui.components.AccountAvatar
import app.vestari.ui.components.CategoryTile
import app.vestari.ui.components.FolderDialog
import app.vestari.ui.components.MessageCard
import app.vestari.ui.components.SoftRow
import app.vestari.ui.components.bouncyClick
import app.vestari.ui.components.iconFor
import app.vestari.ui.sharedCard
import app.vestari.ui.theme.AppTheme
import app.vestari.ui.theme.Palettes
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.key
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import app.vestari.data.ListStyle
import app.vestari.data.Smart
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import app.vestari.ui.components.spacing

@Composable
private fun ScreenTitle(text: String) {
    val settings by Repository.settings.collectAsState()
    app.vestari.ui.components.BrandTitle(
        text, titleStyle(settings.titleSize),
        modifier = Modifier.statusBarsPadding().padding(top = 14.dp, bottom = 10.dp),
    )
}

// ------------------------- Папки -------------------------

@Composable
fun FoldersScreen() {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val categories by Repository.categories.collectAsState()
    val messagesMap by Repository.messages.collectAsState()
    val assignments by Repository.assignments.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var actionsFor by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    val collapsed = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    /** Родитель для «Создать подпапку». */
    var creatingIn by remember { mutableStateOf<String?>(null) }

    val all = remember(messagesMap) { messagesMap.values.flatten() }
    val stats = remember(all, assignments) {
        all.groupBy { categoryOf(it, assignments) }.mapValues { (_, l) -> l.size to l.count { !it.seen } }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Папки") }
        item {
            FolderCard(null, "Все письма", Icons.Outlined.AllInbox, all.size, all.count { !it.seen }) {
                nav.folder = null; nav.tab = Tab.Mail
            }
        }
        // Папки деревом: подпапки — с отступом под родителем, ветки сворачиваются стрелкой.
        items(categoryTree(categories, collapsed.toSet()), key = { it.category.id }) { node ->
            val c = node.category
            val (total, unread) = stats[c.id] ?: (0 to 0)
            FolderCard(
                c.id, c.name, iconFor(c.icon), total, unread,
                modifier = Modifier.animateItem().padding(start = (node.depth * 20).dp),
                onLongClick = { actionsFor = c },
                compact = node.depth > 0,
                expandable = node.hasChildren,
                expanded = c.id !in collapsed,
                onToggle = { if (!collapsed.remove(c.id)) collapsed.add(c.id) },
            ) { nav.folder = c.id; nav.tab = Tab.Mail }
        }
        item {
            SoftRow(Icons.Rounded.Add, "Создать папку", tint = MaterialTheme.colorScheme.primary) { creating = true }
            Text(
                "Удерживайте папку, чтобы переименовать или удалить. Порядок папок — в Профиле, раздел «Порядок папок». " +
                    "Папки, созданные в самой почте (например, в Mail.ru), появляются здесь автоматически.",
                style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, modifier = Modifier.padding(12.dp),
            )
        }
    }

    actionsFor?.let { c ->
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            containerColor = AppTheme.extra.card,
            title = { Text(c.name) },
            text = {
                Column {
                    SoftRow(Icons.Rounded.Add, "Создать подпапку", tint = MaterialTheme.colorScheme.primary) { creatingIn = c.id; creating = true; actionsFor = null }
                    SoftRow(Icons.Outlined.DriveFileRenameOutline, "Переименовать или переместить") { editing = c; actionsFor = null }
                    SoftRow(Icons.Outlined.Delete, "Удалить папку", tint = AppTheme.extra.unreadDot) {
                        actionsFor = null
                        val removed = Repository.removeCategory(c.id)
                        if (nav.folder == c.id) nav.folder = null
                        toasts.show("Папка «${c.name}» удалена", "Отменить",
                            onAction = { removed?.let { (i, cat) -> Repository.restoreCategory(i, cat) } })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionsFor = null }) { Text("Закрыть") } },
        )
    }
    editing?.let { c ->
        FolderDialog("Папка", c.name, c.icon, initialParent = c.parent, editingId = c.id, onDismiss = { editing = null }) { name, icon, parent ->
            Repository.updateCategory(c.copy(name = name.trim(), icon = icon, parent = parent)); editing = null
        }
    }
    if (creating) {
        FolderDialog(
            if (creatingIn != null) "Новая подпапка" else "Новая папка",
            initialParent = creatingIn,
            onDismiss = { creating = false; creatingIn = null },
        ) { name, icon, parent ->
            creating = false; creatingIn = null
            val c = Repository.addCategory(name, icon, parent)
            toasts.show("Папка создана", "Отменить", onAction = { Repository.removeCategory(c.id) })
        }
    }
}

@Composable
private fun FolderCard(
    id: String?, name: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    total: Int, unread: Int,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    compact: Boolean = false,
    expandable: Boolean = false,
    expanded: Boolean = true,
    onToggle: () -> Unit = {},
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp), color = AppTheme.extra.card,
        modifier = modifier.fillMaxWidth().bouncyClick(onLongClick = onLongClick, onClick = onClick),
    ) {
        Row(Modifier.padding(if (compact) 10.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(id, icon, name, if (compact) 36.dp else 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text("$total писем", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
            }
            if (unread > 0) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) { Text(unread.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium) }
            }
            if (expandable) {
                IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        if (expanded) "Свернуть подпапки" else "Показать подпапки", tint = AppTheme.extra.muted,
                    )
                }
            }
        }
    }
}

// ------------------------- Поиск -------------------------

@Composable
fun SearchScreen() {
    val nav = LocalNav.current
    val messagesMap by Repository.messages.collectAsState()
    val categories by Repository.categories.collectAsState()
    val assignments by Repository.assignments.collectAsState()
    val settings by Repository.settings.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val results = remember(query, messagesMap) {
        val q = query.trim().lowercase()
        if (q.length < 2) emptyList()
        else messagesMap.values.flatten().filter {
            it.subject.lowercase().contains(q) || it.fromName.lowercase().contains(q) ||
                it.fromEmail.lowercase().contains(q) || it.preview.lowercase().contains(q)
        }.sortedByDescending { it.date }.take(200)
    }
    val catById = categories.associateBy { it.id }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(settings.listStyle.spacing()),
    ) {
        item {
            Column(Modifier.padding(bottom = 6.dp)) {
                ScreenTitle("Поиск")
                SearchField(query, { query = it }, Modifier.focusRequester(focus))
                val total = remember(messagesMap) { messagesMap.values.sumOf { it.size } }
                Text(
                    if (query.trim().length >= 2) "Найдено: ${results.size}${if (results.size == 200) "+" else ""}"
                    else "Ищу по $total скачанным письмам: отправитель, тема, начало текста",
                    style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
        }
        if (query.trim().length >= 2 && results.isEmpty()) {
            item {
                Text("Ничего не нашлось", color = AppTheme.extra.muted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp))
            }
        }
        items(results, key = { it.key }) { m ->
            MessageCard(
                m, categoryOf(m, assignments)?.let { catById[it] },
                style = settings.listStyle, showPreview = settings.showPreview,
                modifier = Modifier.animateItem().sharedCard(m.key),
                onClick = { nav.push(Route.Message(m.key)) },
                onLongClick = {},
                onStar = { Repository.setFlagged(m, !m.flagged) },
            )
        }
    }
}

/** Поле поиска: аккуратная «пилюля» фиксированной высоты, подсказка в одну строку. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusedColor = MaterialTheme.colorScheme.primary
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) focusedColor.copy(alpha = 0.5f) else Color.Transparent, label = "searchBorder")
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(AppTheme.extra.card)
            .border(1.5.dp, border, RoundedCornerShape(50))
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = AppTheme.extra.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("Поиск по письмам", color = AppTheme.extra.muted, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            }
            BasicTextField(
                value, onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(focusedColor),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Очистить", tint = AppTheme.extra.muted) }
        }
    }
}

// ------------------------- Профиль -------------------------

@Composable
fun ProfileScreen() {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val accounts by Repository.accounts.collectAsState()
    val settings by Repository.settings.collectAsState()
    val syncing by Repository.syncing.collectAsState()
    var removing by remember { mutableStateOf<Account?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Профиль")

        Card {
            Text("Почтовые ящики", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            accounts.forEach { acc ->
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AccountAvatar(acc, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(acc.email, style = MaterialTheme.typography.bodyLarge)
                        Text(Providers.byId(acc.provider).title, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                    }
                    IconButton(onClick = { removing = acc }) { Icon(Icons.Outlined.Delete, "Удалить", tint = AppTheme.extra.muted) }
                }
            }
            SoftRow(Icons.Rounded.Add, "Добавить ящик", tint = MaterialTheme.colorScheme.primary) { nav.push(Route.AddAccount) }
        }

        // «Внешний вид»: живой пример текущего оформления, все настройки — на отдельном экране.
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.extra.card,
            modifier = Modifier.fillMaxWidth().bouncyClick { nav.push(Route.Appearance) },
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                app.vestari.ui.components.LookPreview(settings, width = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Внешний вид", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Логотип, фон, своя картинка, цвета, тема, вид списка и углы карточек",
                        style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = AppTheme.extra.muted)
            }
        }

        Card {
            SwitchRow(
                "Картинки в письмах сразу",
                if (settings.autoImages) "Картинки загружаются при открытии письма"
                else "Только по кнопке — рассылки не узнают, что письмо открыто",
                settings.autoImages,
            ) { v -> Repository.updateSettings { it.copy(autoImages = v) } }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().bouncyClick { nav.push(Route.Smart) },
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Умные функции", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                    Text("Автораскладка, правила, коды из SMS-писем, тихие рассылки",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Card { FolderOrder() }

        Card { app.vestari.update.UpdateCard() }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Notifications, null)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Уведомления о новых письмах", style = MaterialTheme.typography.bodyLarge)
                    Text("Проверка почты примерно раз в 15 минут", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                }
                Switch(settings.notifications, { v -> Repository.updateSettings { it.copy(notifications = v) } })
            }
            SoftRow(Icons.Outlined.Refresh, if (syncing) "Проверяю почту…" else "Проверить почту сейчас") {
                if (!syncing) scope.launch {
                    val fresh = Repository.sync()
                    toasts.show(if (fresh.isEmpty()) "Новых писем нет" else "Новых писем: ${fresh.size}")
                }
            }
            if (settings.notifications) {
                SwitchRow(
                    "Смахнуть — значит прочитано",
                    if (settings.notifySwipeMarksRead) "Смахнутое из шторки письмо отмечается прочитанным и в почте"
                    else "Смахнутое письмо просто скрывается. Прочитать — кнопкой «Прочитано»",
                    settings.notifySwipeMarksRead,
                ) { v -> Repository.updateSettings { it.copy(notifySwipeMarksRead = v) } }
                Text("Писем в шторке на один ящик", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
                val maxOptions = listOf(1, 3, 5)
                Segmented(maxOptions.map { it.toString() }, maxOptions.indexOf(settings.notifyMax).coerceAtLeast(0)) { i ->
                    Repository.updateSettings { it.copy(notifyMax = maxOptions[i]) }
                }
            }
        }

        Card { LoadingSettings() }

        Text(
            "Vestari работает с почтой напрямую по IMAP/SMTP. Данные и пароли хранятся только на телефоне.",
            style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(120.dp))
    }

    removing?.let { acc ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = AppTheme.extra.card,
            title = { Text("Удалить ящик?") },
            text = { Text("${acc.email} пропадёт из приложения вместе с сохранёнными письмами. На сервере почты ничего не удалится.") },
            confirmButton = {
                TextButton(onClick = { Repository.removeAccount(acc.id); removing = null; toasts.show("Ящик удалён") }) {
                    Text("Удалить", color = AppTheme.extra.unreadDot)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Отмена") } },
        )
    }
}

// ------------------------- Умные функции -------------------------

@Composable
fun SmartScreen() {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val settings by Repository.settings.collectAsState()
    val rules by Repository.rules.collectAsState()
    val categories by Repository.categories.collectAsState()
    val messagesMap by Repository.messages.collectAsState()
    var addingRule by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val stats = remember(messagesMap) {
        val all = messagesMap.values.flatten()
        Triple(all.size, all.count { it.autoCategory != null }, all.count { Smart.isFresh(it) && Smart.otp(it) != null })
    }
    val catName = categories.associate { it.id to it.name }

    /** Изменили настройку, влияющую на раскладку, — сразу перераспределяем письма. */
    fun changeSorting(block: (app.vestari.data.Settings) -> app.vestari.data.Settings) {
        Repository.updateSettings(block)
        busy = true
        Repository.redistributeAsync { n ->
            busy = false
            if (n > 0) toasts.show("Перераспределено писем: $n")
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.statusBarsPadding().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                app.vestari.ui.components.BrandTitle("Умные функции", titleStyle(settings.titleSize))
            }

            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Разложено автоматически: ${stats.second} из ${stats.first} писем",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Всё работает прямо на телефоне — письма никуда не отправляются.",
                    style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        busy = true
                        Repository.redistributeAsync { n ->
                            busy = false
                            toasts.show(if (n == 0) "Всё уже разложено по местам" else "Перераспределено писем: $n")
                        }
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (busy) "Перераспределяю…" else "Перераспределить все письма")
                }
            }

            Card {
                Text("Раскладка по папкам", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                SwitchRow(
                    "Автораскладка",
                    "Чеки, скидки, билеты, рассылки, госписьма и письма себе определяются по отправителю и теме",
                    settings.autoSort,
                ) { v -> changeSorting { it.copy(autoSort = v) } }
                SwitchRow(
                    "Учиться на моих переносах",
                    "Перенесли письмо в папку — остальные письма этого отправителя (и будущие) пойдут туда же",
                    settings.learnFromMoves,
                ) { v -> Repository.updateSettings { it.copy(learnFromMoves = v) } }
                SwitchRow(
                    "Папки из почты",
                    "Папки, созданные в Mail.ru и других ящиках, становятся папками Vestari",
                    settings.serverFolders,
                ) { v ->
                    changeSorting { it.copy(serverFolders = v) }
                    if (v) Repository.refresh()
                }
            }

            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Правила", style = MaterialTheme.typography.titleMedium)
                        Text("Важнее автораскладки. Адрес — один отправитель, @домен — весь магазин или сервис.",
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                    }
                    IconButton(onClick = { addingRule = true }) {
                        Icon(Icons.Rounded.Add, "Добавить правило", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (rules.isEmpty()) {
                    Text(
                        "Пока правил нет. Они появятся сами, когда вы перенесёте письмо в папку, или добавьте вручную.",
                        style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, modifier = Modifier.padding(top = 8.dp),
                    )
                }
                rules.sortedByDescending { it.created }.forEach { r ->
                    key(r.pattern) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.pattern, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(
                                    "→ " + (if (r.categoryId.isEmpty()) "без папки" else catName[r.categoryId] ?: "удалённая папка"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = {
                                Repository.removeRule(r.pattern)
                                Repository.redistributeAsync()
                                toasts.show("Правило удалено", "Отменить", onAction = {
                                    Repository.addRule(r.pattern, r.categoryId.ifEmpty { null })
                                    Repository.redistributeAsync()
                                })
                            }) { Icon(Icons.Outlined.Delete, "Удалить правило", tint = AppTheme.extra.muted) }
                        }
                    }
                }
            }

            Card {
                Text("Список и уведомления", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                SwitchRow(
                    "Коды подтверждения",
                    "Код из письма виден прямо в списке и в уведомлении — нажмите, чтобы скопировать" +
                        if (stats.third > 0) " (сейчас: ${stats.third})" else "",
                    settings.otpCodes,
                ) { v -> Repository.updateSettings { it.copy(otpCodes = v) } }
                SwitchRow(
                    "Тихие рассылки и скидки",
                    "Без уведомлений — заглянете, когда будет время",
                    settings.quietPromos,
                ) { v -> Repository.updateSettings { it.copy(quietPromos = v) } }
                SwitchRow(
                    "Чистые «Все»",
                    "Рассылки и скидки не показываются во «Все», только в своих папках",
                    settings.cleanInbox,
                ) { v -> Repository.updateSettings { it.copy(cleanInbox = v) } }
                SwitchRow(
                    "Сворачивать папки в строку",
                    "Как в Mail.ru: во «Все» письма папки собраны в одну строку «Рассылки · New · Ozon, Сбер…» на месте самого свежего",
                    settings.bundleFolders,
                ) { v -> Repository.updateSettings { it.copy(bundleFolders = v) } }
                AnimatedVisibility(settings.bundleFolders) {
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        categories.forEach { c ->
                            val on = c.id in settings.bundled
                            app.vestari.ui.components.FolderChip(
                                c.name, on, icon = app.vestari.ui.components.iconFor(c.icon),
                            ) {
                                Repository.updateSettings { s -> s.copy(bundled = if (on) s.bundled - c.id else s.bundled + c.id) }
                            }
                        }
                    }
                }
                SwitchRow(
                    "Группировать по датам",
                    "Разделители «Сегодня», «Вчера», «На этой неделе»…",
                    settings.groupByDate,
                ) { v -> Repository.updateSettings { it.copy(groupByDate = v) } }
            }
            Spacer(Modifier.navigationBarsPadding().height(40.dp))
        }
    }

    if (addingRule) {
        RuleDialog(categories, onDismiss = { addingRule = false }) { pattern, catId ->
            addingRule = false
            Repository.addRule(pattern, catId)
            busy = true
            Repository.redistributeAsync { n ->
                busy = false
                toasts.show(if (n > 0) "Правило добавлено, перенесено писем: $n" else "Правило добавлено")
            }
        }
    }
}

@Composable
private fun RuleDialog(categories: List<Category>, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var pattern by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(categories.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.extra.card,
        title = { Text("Новое правило") },
        text = {
            Column {
                OutlinedTextField(
                    pattern, { pattern = it.trim() },
                    label = { Text("Адрес или @домен") },
                    placeholder = { Text("@ozon.ru") },
                    singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Класть в папку", style = MaterialTheme.typography.labelLarge)
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    categoryTree(categories).forEach { node ->
                        val c = node.category
                        Row(
                            Modifier.fillMaxWidth().padding(start = (node.depth * 18).dp).clip(RoundedCornerShape(12.dp)).clickable { selected = c.id }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected == c.id, { selected = c.id })
                            CategoryTile(c.id, iconFor(c.icon), c.name, 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(c.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(pattern, selected) }, enabled = pattern.length >= 3) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onChange(!checked) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
        }
        Switch(checked, onChange)
    }
}

/** Порядок папок: стрелками вверх/вниз. Тот же порядок — в строке папок на экране «Почта». */
@Composable
private fun FolderOrder() {
    val categories by Repository.categories.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Порядок папок", style = MaterialTheme.typography.titleMedium)
            Text("Как папки идут в строке на экране «Почта»", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
        }
        val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
        Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.rotate(rot))
    }
    AnimatedVisibility(expanded) {
        Column(Modifier.padding(top = 8.dp)) {
            // Деревом: стрелки двигают папку среди папок её уровня, подпапки едут вместе с ней.
            categoryTree(categories).forEach { node ->
                val c = node.category
                val siblings = categories.filter { categories.isTopLevel(it) == categories.isTopLevel(c) && (categories.isTopLevel(c) || it.parent == c.parent) }
                val i = siblings.indexOfFirst { it.id == c.id }
                key(c.id) {
                    Row(Modifier.fillMaxWidth().padding(start = (node.depth * 20).dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryTile(c.id, iconFor(c.icon), c.name, if (node.depth > 0) 28.dp else 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { Repository.moveAmongSiblings(c.id, -1) }, enabled = i > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Выше")
                        }
                        IconButton(onClick = { Repository.moveAmongSiblings(c.id, 1) }, enabled = i < siblings.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Ниже")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = AppTheme.extra.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** «Загрузка писем»: ход, пауза, размер пачки, сколько писем хранить, только Wi-Fi, значок в строке состояния. */
@Composable
private fun LoadingSettings() {
    val settings by Repository.settings.collectAsState()
    val progressMap by Repository.progress.collectAsState()
    val loaded = progressMap.values.sumOf { it.loaded }
    val total = progressMap.values.sumOf { it.total }
    val loading = progressMap.values.any { it.loading }
    val note = progressMap.values.firstNotNullOfOrNull { it.note }
    fun fmt(n: Int) = "%,d".format(n).replace(',', ' ')

    Text("Загрузка писем", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
    Text(
        when {
            total == 0 -> "Письма ещё не считались"
            loaded >= total -> "Загружено всё: ${fmt(total)} писем"
            note != null -> "$note · ${fmt(loaded)} из ${fmt(total)}"
            else -> "${fmt(loaded)} из ${fmt(total)} · ${(loaded * 100L / total)}%"
        },
        style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
    )
    if (total > 0 && loaded < total) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { (loaded.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        )
        SoftRow(
            Icons.Outlined.Refresh,
            if (settings.syncPaused) "Продолжить загрузку" else if (loading) "Поставить на паузу" else "Догрузить сейчас",
        ) { Repository.setLoadingPaused(!settings.syncPaused && loading) }
    }

    Text("Скорость", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
    Segmented(SyncBatchOptions.map { it.first }, SyncBatchOptions.indexOfFirst { it.second == settings.syncBatch }.coerceAtLeast(0)) { i ->
        Repository.updateSettings { it.copy(syncBatch = SyncBatchOptions[i].second) }
    }
    Text(
        "Сколько писем просить у сервера за раз. «Быстро» — меньше ожидания, но больше трафика за раз",
        style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )

    Text("Хранить писем в каждой папке", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
    Segmented(SyncLimitOptions.map { it.first }, SyncLimitOptions.indexOfFirst { it.second == settings.syncLimit }.coerceAtLeast(0)) { i ->
        Repository.updateSettings { it.copy(syncLimit = SyncLimitOptions[i].second) }
        Repository.refresh()
    }
    Text(
        "Самые новые. Старые письма остаются на сервере, их можно найти в почте",
        style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )

    SwitchRow("Только по Wi-Fi", "Старые письма докачиваются, только когда нет мобильного трафика. Новые приходят всегда", settings.syncWifiOnly) { v ->
        Repository.updateSettings { it.copy(syncWifiOnly = v) }
        Repository.refresh()
    }
    SwitchRow("Ход загрузки в строке состояния", "Процент рядом с часами (Android 16) и уведомление с кнопкой «Пауза»", settings.syncProgressNotice) { v ->
        Repository.updateSettings { it.copy(syncProgressNotice = v) }
    }
}

@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(AppTheme.extra.accentSoft).padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val bg by animateColorAsState(if (i == selected) AppTheme.extra.card else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(bg).bouncyClick { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

