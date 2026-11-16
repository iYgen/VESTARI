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
import app.vestari.data.Account
import app.vestari.data.Category
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
import app.vestari.ui.components.spacing

@Composable
private fun ScreenTitle(text: String) {
    val settings by Repository.settings.collectAsState()
    Text(
        text, style = titleStyle(settings.titleSize),
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
        items(categories, key = { it.id }) { c ->
            val (total, unread) = stats[c.id] ?: (0 to 0)
            FolderCard(
                c.id, c.name, iconFor(c.icon), total, unread,
                modifier = Modifier.animateItem(),
                onLongClick = { actionsFor = c },
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
                    SoftRow(Icons.Outlined.DriveFileRenameOutline, "Переименовать") { editing = c; actionsFor = null }
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
        FolderDialog("Папка", c.name, c.icon, onDismiss = { editing = null }) { name, icon ->
            Repository.updateCategory(c.copy(name = name.trim(), icon = icon)); editing = null
        }
    }
    if (creating) {
        FolderDialog("Новая папка", onDismiss = { creating = false }) { name, icon ->
            creating = false
            val c = Repository.addCategory(name, icon)
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
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp), color = AppTheme.extra.card,
        modifier = modifier.fillMaxWidth().bouncyClick(onLongClick = onLongClick, onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(id, icon, name, 46.dp)
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

        Card {
            Text("Цветовая гамма", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Palettes.forEachIndexed { i, p ->
                    val selected = settings.palette == i
                    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, label = "ring")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .border(ring, p.accent, CircleShape)
                                .padding(5.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(p.bgBottom, p.accent)))
                                .bouncyClick { Repository.updateSettings { it.copy(palette = i) } },
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        Text(p.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Тема", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Segmented(listOf("Как в системе", "Светлая", "Тёмная"), settings.darkMode) { m ->
                Repository.updateSettings { it.copy(darkMode = m) }
            }
        }

        Card {
            Text("Вид списка писем", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Segmented(ListStyle.entries.map { it.title }, settings.listStyle.ordinal) { i ->
                Repository.updateSettings { it.copy(listStyle = ListStyle.entries[i]) }
            }
            Text(settings.listStyle.hint, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted,
                modifier = Modifier.padding(start = 8.dp, top = 6.dp))
            Spacer(Modifier.height(16.dp))
            Text("Размер заголовков", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Segmented(listOf("Маленький", "Средний", "Крупный"), settings.titleSize) { s ->
                Repository.updateSettings { it.copy(titleSize = s) }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow("Превью текста письма", "Первые строки письма под темой", settings.showPreview) { v ->
                Repository.updateSettings { it.copy(showPreview = v) }
            }
            SwitchRow(
                "Картинки в письмах сразу",
                if (settings.autoImages) "Картинки загружаются при открытии письма"
                else "Только по кнопке — рассылки не узнают, что письмо открыто",
                settings.autoImages,
            ) { v -> Repository.updateSettings { it.copy(autoImages = v) } }
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
        }

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

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
            categories.forEachIndexed { i, c ->
                key(c.id) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryTile(c.id, iconFor(c.icon), c.name, 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { Repository.moveCategory(i, i - 1) }, enabled = i > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Выше")
                        }
                        IconButton(onClick = { Repository.moveCategory(i, i + 1) }, enabled = i < categories.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Ниже")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = AppTheme.extra.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
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
