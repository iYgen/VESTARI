@file:OptIn(ExperimentalMaterial3Api::class)

package app.vestari.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vestari.data.Account
import app.vestari.data.Category
import app.vestari.data.categoryTree
import app.vestari.data.childrenOf
import app.vestari.data.subtreeIds
import app.vestari.data.pathTo
import app.vestari.data.isTopLevel
import app.vestari.data.MailMessage
import app.vestari.data.RemoteFolder
import app.vestari.data.Repository
import app.vestari.ui.LocalNav
import app.vestari.ui.LocalToasts
import app.vestari.ui.Route
import app.vestari.ui.Tab
import app.vestari.ui.components.CategoryTile
import app.vestari.ui.components.MessageCard
import app.vestari.ui.components.formatDate
import app.vestari.ui.components.iconFor
import app.vestari.ui.components.spacing
import app.vestari.ui.components.tileColors
import app.vestari.ui.sharedCard
import app.vestari.ui.theme.AppTheme
import kotlinx.coroutines.launch

// ============================ свёрнутая строка папки ============================

/** Письма одной папки, свёрнутые в строку во «Все»: показывается там, где самое свежее письмо. */
data class Bundle(val category: Category, val messages: List<MailMessage>) {
    val latest: Long get() = messages.first().date
    val unread: Int get() = messages.count { !it.seen }
    /** Отправители без повторов, самые свежие первыми: «СберБанк, Ozon, DRIVE2.RU…». */
    val senders: String get() = messages.asSequence().map { it.senderTitle.trim().trim('"') }.distinct().take(12).joinToString(", ")
}

@Composable
fun BundleRow(b: Bundle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (_, fg) = tileColors(b.category.id, AppTheme.extra.isDark)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AppTheme.extra.card.copy(alpha = if (AppTheme.extra.isDark) 1f else 0.7f),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(iconFor(b.category.icon), null, tint = fg, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        b.category.name, color = fg, style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (b.unread > 0) FontWeight.Bold else FontWeight.Medium,
                    )
                    if (b.unread > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (b.unread > 1) "New · ${b.unread}" else "New",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(formatDate(b.latest), style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                }
                Text(
                    b.senders, style = MaterialTheme.typography.bodyMedium, color = AppTheme.extra.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.KeyboardArrowRight, null, tint = AppTheme.extra.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Подпапка внутри открытой папки: значок, название (жирное, если есть непрочитанные), число писем, «›». */
@Composable
fun SubfolderRow(
    icon: ImageVector,
    tint: Color,
    name: String,
    unread: Int,
    total: Int?,
    hasChildren: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AppTheme.extra.card.copy(alpha = if (AppTheme.extra.isDark) 1f else 0.7f),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                if (unread > 0) "$name (${if (unread > 999) "999+" else unread})" else name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (hasChildren) {
                Icon(Icons.Outlined.Folder, "Есть подпапки", tint = AppTheme.extra.muted, modifier = Modifier.padding(start = 6.dp).size(16.dp))
            }
            if (total != null && total > 0) {
                Text(total.toString(), style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, modifier = Modifier.padding(start = 8.dp))
            }
            Icon(Icons.Rounded.KeyboardArrowRight, null, tint = AppTheme.extra.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Путь к вложенной папке: «Работа › Проекты › 2024». Нажатие на звено — переход в эту папку. */
@Composable
fun FolderPath(path: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    if (path.size < 2) return
    androidx.compose.foundation.lazy.LazyRow(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        path.forEachIndexed { i, (name, go) ->
            item(key = "p$i") {
                val last = i == path.lastIndex
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !last, onClick = go)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                    if (!last) Icon(Icons.Rounded.KeyboardArrowRight, null, tint = AppTheme.extra.muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ============================ панель папок ============================

private fun roleIcon(role: String): ImageVector = when (role) {
    "inbox" -> Icons.Outlined.Inbox
    "sent" -> Icons.AutoMirrored.Outlined.Send
    "drafts" -> Icons.Outlined.Drafts
    "spam" -> Icons.Outlined.Report
    "trash" -> Icons.Outlined.Delete
    "archive" -> Icons.Outlined.Archive
    "outbox" -> Icons.Outlined.Outbox
    else -> Icons.Outlined.Folder
}

/** Порядок служебных папок внизу панели — как в почтовых клиентах. */
private val systemOrder = listOf("sent", "drafts", "outbox", "archive", "spam", "trash")

/** Строка дерева: папка и её глубина вложенности. */
private data class TreeNode(val folder: RemoteFolder, val depth: Int, val hasChildren: Boolean)

/**
 * Дерево папок ящика: «Входящие» со всеми подпапками, затем папки пользователя (со своими подпапками),
 * затем служебные. Свёрнутые ветки ([collapsed]) пропускаются.
 */
private fun buildTree(folders: List<RemoteFolder>, collapsed: Set<String>): List<TreeNode> {
    val byParent = folders.groupBy { f -> f.parent?.takeIf { p -> folders.any { it.fullName == p } } }
    val out = mutableListOf<TreeNode>()
    fun walk(f: RemoteFolder, depth: Int) {
        val kids = byParent[f.fullName].orEmpty().sortedBy { it.displayName.lowercase() }
        out += TreeNode(f, depth, kids.isNotEmpty())
        if (f.fullName !in collapsed) kids.filter { it.role.isEmpty() || it.role == "archive" }.forEach { walk(it, depth + 1) }
    }
    val roots = byParent[null].orEmpty()
    roots.filter { it.isInbox }.forEach { walk(it, 0) }
    roots.filter { it.role.isEmpty() }.sortedBy { it.displayName.lowercase() }.forEach { walk(it, 0) }
    roots.filter { it.role in systemOrder }.sortedBy { systemOrder.indexOf(it.role) }.forEach { walk(it, 0) }
    return out
}

/**
 * Панель папок «как в iOS»: выезжает снизу до половины экрана, тянется выше и прокручивается.
 * Вверху — дерево папок ящика (с подпапками), ниже — папки Vestari.
 */
@Composable
fun FolderPanel(onDismiss: () -> Unit) {
    val nav = LocalNav.current
    val accounts by Repository.accounts.collectAsState()
    val settings by Repository.settings.collectAsState()
    val serverFolders by Repository.serverFolders.collectAsState()
    val remoteUnread by Repository.remoteUnread.collectAsState()
    val messagesMap by Repository.messages.collectAsState()
    val categories by Repository.categories.collectAsState()
    val assignments by Repository.assignments.collectAsState()
    val drafts by Repository.drafts.collectAsState()
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val selected = settings.selectedAccountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.singleOrNull()?.id
    val shown = if (selected != null) accounts.filter { it.id == selected } else accounts

    // Непрочитанные по папкам сервера — из скачанных писем; для служебных — со счётчика сервера.
    val unreadByFolder = remember(messagesMap) {
        messagesMap.mapValues { (_, l) -> l.filter { !it.seen }.groupingBy { it.folder }.eachCount() }
    }
    val totalByFolder = remember(messagesMap) {
        messagesMap.mapValues { (_, l) -> l.groupingBy { it.folder }.eachCount() }
    }

    fun openServer(acc: Account, f: RemoteFolder) {
        onDismiss()
        if (f.isInbox) {
            Repository.updateSettings { it.copy(selectedAccountId = acc.id) }
            nav.folder = null; nav.tab = Tab.Mail
        } else nav.push(Route.ServerFolder(acc.id, f.fullName, f.displayName))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = AppTheme.extra.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
        ) {
            item {
                Text("Папки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
            }
            shown.forEach { acc ->
                val folders = serverFolders[acc.id].orEmpty()
                if (shown.size > 1) item(key = "acc_${acc.id}") {
                    Text(
                        acc.email, style = MaterialTheme.typography.labelLarge, color = AppTheme.extra.muted,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                if (folders.isEmpty()) item(key = "empty_${acc.id}") {
                    Text(
                        "Список папок появится после первой проверки почты.",
                        style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                val tree = buildTree(folders, collapsed.filterValues { it }.keys)
                var systemStarted = false
                tree.forEach { node ->
                    val f = node.folder
                    val isSystem = f.role in systemOrder && node.depth == 0
                    if (isSystem && !systemStarted) {
                        systemStarted = true
                        item(key = "div_${acc.id}") { HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
                    }
                    item(key = "f_${acc.id}_${f.fullName}") {
                        val unread = if (f.syncable) unreadByFolder[acc.id]?.get(f.fullName) ?: 0 else remoteUnread[acc.id]?.get(f.fullName) ?: 0
                        FolderLine(
                            icon = roleIcon(f.role), title = f.displayName, depth = node.depth,
                            unread = if (f.role == "trash" || f.role == "sent") 0 else unread,
                            total = if (f.syncable) totalByFolder[acc.id]?.get(f.fullName) else null,
                            expandable = node.hasChildren,
                            expanded = collapsed[f.fullName] != true,
                            onToggle = { collapsed[f.fullName] = collapsed[f.fullName] != true },
                            onClick = { openServer(acc, f) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            item(key = "vestari") {
                HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    "Папки Vestari", style = MaterialTheme.typography.labelLarge, color = AppTheme.extra.muted,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 4.dp),
                )
            }
            val all = messagesMap.values.flatten()
            item(key = "v_flagged") {
                FolderLine(Icons.Outlined.StarOutline, "Важные", 0, unread = 0, total = all.count { it.flagged },
                    onClick = { onDismiss(); nav.folder = FLAGGED; nav.tab = Tab.Mail })
            }
            if (drafts.isNotEmpty()) item(key = "v_drafts") {
                FolderLine(Icons.Outlined.Drafts, "Черновики Vestari", 0, unread = 0, total = drafts.size,
                    onClick = { onDismiss(); nav.push(Route.Drafts) })
            }
            // Папки Vestari — деревом, с подпапками на любую глубину.
            val vCollapsed = collapsed.filter { it.value && it.key.startsWith("v:") }.keys.map { it.removePrefix("v:") }.toSet()
            items(categoryTree(categories, vCollapsed), key = { "v_${it.category.id}" }) { node ->
                val c = node.category
                val list = all.filter { categoryOf(it, assignments) == c.id }
                FolderLine(
                    iconFor(c.icon), c.name, node.depth,
                    unread = list.count { !it.seen },
                    total = list.size, tileId = c.id,
                    expandable = node.hasChildren,
                    expanded = collapsed["v:" + c.id] != true,
                    onToggle = { collapsed["v:" + c.id] = collapsed["v:" + c.id] != true },
                    onClick = { onDismiss(); nav.folder = c.id; nav.tab = Tab.Mail },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun FolderLine(
    icon: ImageVector,
    title: String,
    depth: Int,
    unread: Int,
    total: Int?,
    modifier: Modifier = Modifier,
    tileId: String? = null,
    expandable: Boolean = false,
    expanded: Boolean = true,
    onToggle: () -> Unit = {},
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 8.dp + (depth * 22).dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (tileId != null) tileColors(tileId, AppTheme.extra.isDark).second else MaterialTheme.colorScheme.primary
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        // Есть непрочитанные — название жирное и «(N)», иначе обычный шрифт.
        Text(
            if (unread > 0) "$title (${if (unread > 999) "999+" else unread})" else title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (unread == 0 && total != null && total > 0) {
            Text(total.toString(), style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
        }
        if (expandable) {
            val rot by animateFloatAsState(if (expanded) 90f else 0f, label = "branch")
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.KeyboardArrowRight, if (expanded) "Свернуть" else "Раскрыть", tint = AppTheme.extra.muted, modifier = Modifier.rotate(rot))
            }
        } else Spacer(Modifier.width(if (unread > 0 || (total ?: 0) > 0) 8.dp else 0.dp))
    }
}

// ============================ папка на сервере ============================

/**
 * Просмотр папки ящика. Скачанные папки («Входящие», папки пользователя) показываются сразу из памяти,
 * служебные (отправленные, спам, корзина, архив) подгружаются с сервера страницами.
 */
@Composable
fun ServerFolderScreen(accountId: String, folder: String, title: String) {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val settings by Repository.settings.collectAsState()
    val serverFolders by Repository.serverFolders.collectAsState()
    val messagesMap by Repository.messages.collectAsState()
    val remoteMap by Repository.remote.collectAsState()
    val categories by Repository.categories.collectAsState()
    val assignments by Repository.assignments.collectAsState()
    val info = serverFolders[accountId]?.firstOrNull { it.fullName == folder }
    val local = info?.syncable == true

    var loading by remember { mutableStateOf(!local) }
    var hasMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val list = remember(messagesMap, remoteMap, local) {
        if (local) messagesMap[accountId].orEmpty().filter { it.folder == folder }
        else remoteMap["$accountId|$folder"].orEmpty()
    }
    val catById = categories.associateBy { it.id }
    val listState = rememberLazyListState()

    fun load(reset: Boolean) {
        loading = true; error = null
        scope.launch {
            runCatching { Repository.loadRemoteFolder(accountId, folder, reset) }
                .onSuccess { hasMore = it }
                .onFailure { error = it.message ?: "Не удалось загрузить папку" }
            loading = false
        }
    }
    LaunchedEffect(folder) { if (!local) load(reset = true) }

    // Долистали до конца — подгружаем следующую страницу.
    val nearEnd by remember { androidx.compose.runtime.derivedStateOf {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        last >= listState.layoutInfo.totalItemsCount - 5
    } }
    LaunchedEffect(nearEnd, hasMore, loading) { if (nearEnd && hasMore && !loading && !local) load(reset = false) }

    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.statusBarsPadding().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                Column(Modifier.weight(1f)) {
                    app.vestari.ui.components.BrandTitle(title, MaterialTheme.typography.titleLarge)
                    val total = if (local) list.size else Repository.remoteTotal(accountId, folder)
                    if (total != null) Text("$total писем", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                }
                if (loading) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
            }
            LazyColumn(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(settings.listStyle.spacing()),
            ) {
                error?.let { e ->
                    item {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(e, color = AppTheme.extra.unreadDot)
                            TextButton(onClick = { load(reset = list.isEmpty()) }) { Text("Повторить") }
                        }
                    }
                }
                // Путь «Входящие › Работа › Проекты» — по нему можно подняться на любой уровень.
                val folders = serverFolders[accountId].orEmpty()
                info?.let { f ->
                    val chain = f.parts.indices.map { i -> f.parts.take(i + 1).joinToString(f.separator) }
                        .mapNotNull { full -> folders.firstOrNull { it.fullName == full } }
                    if (chain.size > 1) item(key = "path") {
                        FolderPath(chain.map { p ->
                            p.displayName to {
                                nav.pop()
                                if (p.isInbox) { nav.folder = null; nav.tab = Tab.Mail }
                                else nav.push(Route.ServerFolder(accountId, p.fullName, p.displayName))
                            }
                        })
                    }
                }
                // Подпапки — над письмами, любой глубины: внутри каждой снова видны её подпапки.
                val subs = folders.filter { it.parent == folder }.sortedBy { it.displayName.lowercase() }
                items(subs, key = { "sub_" + it.fullName }) { s ->
                    val unread = messagesMap[accountId].orEmpty().count { it.folder == s.fullName && !it.seen }
                    SubfolderRow(
                        roleIcon(s.role), MaterialTheme.colorScheme.primary, s.displayName,
                        unread = unread, total = if (s.syncable) messagesMap[accountId].orEmpty().count { it.folder == s.fullName } else null,
                        hasChildren = folders.any { it.parent == s.fullName },
                        modifier = Modifier.animateItem(),
                    ) { nav.push(Route.ServerFolder(accountId, s.fullName, s.displayName)) }
                }
                if (!loading && error == null && list.isEmpty() && subs.isEmpty()) item {
                    Text("Писем нет", color = AppTheme.extra.muted, modifier = Modifier.fillMaxWidth().padding(top = 48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                items(list, key = { it.key }) { m ->
                    MessageCard(
                        m, categoryOf(m, assignments)?.let { catById[it] },
                        style = settings.listStyle, showPreview = settings.showPreview,
                        modifier = Modifier.animateItem().sharedCard(m.key),
                        onClick = { nav.push(Route.Message(m.key)) },
                        onLongClick = {},
                        onStar = { if (local) Repository.setFlagged(m, !m.flagged) else toasts.show("Отметки здесь пока только для просмотра") },
                    )
                }
            }
        }
    }
}
