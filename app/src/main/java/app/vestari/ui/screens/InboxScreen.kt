@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.vestari.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MarkunreadMailbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vestari.data.Category
import app.vestari.data.MailMessage
import app.vestari.data.Repository
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
import app.vestari.ui.sharedCard
import app.vestari.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** Категория письма с учётом ручной раскладки. */
fun categoryOf(m: MailMessage, assignments: Map<String, String>): String? {
    val manual = assignments[m.key]
    return when {
        manual == null -> m.autoCategory
        manual.isEmpty() -> null
        else -> manual
    }
}

@Composable
fun InboxScreen() {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()

    val accounts by Repository.accounts.collectAsState()
    val settings by Repository.settings.collectAsState()
    val messagesMap by Repository.messages.collectAsState()
    val categories by Repository.categories.collectAsState()
    val assignments by Repository.assignments.collectAsState()
    val syncing by Repository.syncing.collectAsState()

    var moveTarget by remember { mutableStateOf<MailMessage?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var autoSynced by rememberSaveable { mutableStateOf(false) }

    val selectedId = settings.selectedAccountId?.takeIf { id -> accounts.any { it.id == id } }
        ?: accounts.singleOrNull()?.id
    val selectedAccount = accounts.firstOrNull { it.id == selectedId }
    val all: List<MailMessage> = remember(messagesMap, selectedId) {
        if (selectedId == null) messagesMap.values.flatten().sortedByDescending { it.date }
        else messagesMap[selectedId].orEmpty()
    }
    val catById = categories.associateBy { it.id }
    val visible = remember(all, nav.folder, assignments) {
        if (nav.folder == null) all else all.filter { categoryOf(it, assignments) == nav.folder }
    }
    val unreadByCat = remember(all, assignments) {
        all.filter { !it.seen }.groupingBy { categoryOf(it, assignments) }.eachCount()
    }

    LaunchedEffect(accounts.size) {
        if (accounts.isNotEmpty() && !autoSynced) { autoSynced = true; Repository.sync() }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { scope.launch { Repository.sync(selectedId) } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 190.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                        Header(
                            avatar = { AccountAvatar(selectedAccount, 48.dp) },
                            title = nav.folder?.let { catById[it]?.name } ?: "Входящие",
                            onAccounts = { nav.accountSheetOpen = true },
                            onSearch = { nav.tab = Tab.Search },
                            onCompose = { nav.push(Route.Compose()) },
                        )
                        if (accounts.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                AccountPill(selectedAccount?.email ?: "Все ящики") { nav.accountSheetOpen = true }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FolderChip("Все", nav.folder == null, badge = 0) { nav.folder = null }
                            categories.forEach { c ->
                                FolderChip(c.name, nav.folder == c.id, badge = unreadByCat[c.id] ?: 0) {
                                    nav.folder = if (nav.folder == c.id) null else c.id
                                }
                            }
                            AddChip { creatingFolder = true }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                if (accounts.isEmpty()) {
                    item(key = "empty-accounts") { NoAccountsCard { nav.push(Route.AddAccount) } }
                } else if (visible.isEmpty() && !syncing) {
                    item(key = "empty") {
                        Text(
                            if (nav.folder == null) "Писем пока нет" else "В этой папке пусто",
                            color = AppTheme.extra.muted, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        )
                    }
                }

                items(visible, key = { it.key }) { m ->
                    SwipeableMessage(
                        m = m,
                        category = categoryOf(m, assignments)?.let { catById[it] },
                        showAccount = if (selectedId == null && accounts.size > 1) accounts.firstOrNull { it.id == m.accountId }?.email else null,
                        modifier = Modifier.animateItem(),
                        onOpen = { nav.push(Route.Message(m.key)) },
                        onMove = { moveTarget = m },
                        onDelete = {
                            Repository.hideLocally(m)
                            toasts.show("Письмо удалено", "Отменить",
                                onAction = { Repository.unhide(m) },
                                onTimeout = { Repository.commitDelete(m) })
                        },
                    )
                }
            }
        }

        ComposeFab(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 100.dp)) {
            nav.push(Route.Compose())
        }
    }

    if (nav.accountSheetOpen) AccountSheet { nav.accountSheetOpen = false }
    moveTarget?.let { MoveToFolderSheet(it) { moveTarget = null } }
    if (creatingFolder) {
        FolderDialog("Новая папка", onDismiss = { creatingFolder = false }) { name, icon ->
            creatingFolder = false
            val c = Repository.addCategory(name, icon)
            toasts.show("Папка создана", "Отменить", onAction = { Repository.removeCategory(c.id) })
        }
    }
}

@Composable
private fun Header(
    avatar: @Composable () -> Unit,
    title: String,
    onAccounts: () -> Unit,
    onSearch: () -> Unit,
    onCompose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).bouncyClick(onClick = onAccounts).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            avatar()
            Icon(Icons.Rounded.KeyboardArrowDown, "Аккаунты", tint = AppTheme.extra.muted)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, modifier = Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Поиск", modifier = Modifier.size(28.dp)) }
        IconButton(onClick = onCompose) {
            Icon(Icons.Outlined.EditNote, "Написать", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
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
private fun AddChip(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(42.dp)
            .clip(CircleShape)
            .background(AppTheme.extra.card.copy(alpha = 0.85f))
            .bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, "Новая папка", tint = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun SwipeableMessage(
    m: MailMessage,
    category: Category?,
    showAccount: String?,
    modifier: Modifier,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.35f },
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> { Repository.setSeen(m, !m.seen); false } // вернётся на место
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val dir = state.dismissDirection
            val color by animateColorAsState(
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                    SwipeToDismissBoxValue.EndToStart -> AppTheme.extra.unreadDot
                    else -> Color.Transparent
                }, label = "swipeBg",
            )
            val iconScale by animateFloatAsState(if (state.progress > 0.3f) 1.2f else 0.9f, label = "swipeIcon")
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(color).padding(horizontal = 28.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Icon(
                    when (dir) {
                        SwipeToDismissBoxValue.EndToStart -> Icons.Outlined.Delete
                        else -> if (m.seen) Icons.Outlined.MarkEmailUnread else Icons.Outlined.MarkEmailRead
                    },
                    null, tint = Color.White,
                    modifier = Modifier.scale(iconScale).alpha(if (dir == SwipeToDismissBoxValue.Settled) 0f else 1f),
                )
            }
        },
    ) {
        MessageCard(
            m = m,
            category = category,
            showAccount = showAccount,
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
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(AppTheme.extra.accentSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.MarkunreadMailbox, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp)) }
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
