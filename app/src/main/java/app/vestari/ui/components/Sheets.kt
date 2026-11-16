@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.vestari.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.vestari.data.Account
import app.vestari.data.Category
import app.vestari.data.MailMessage
import app.vestari.data.Repository
import app.vestari.ui.LocalNav
import app.vestari.ui.LocalToasts
import app.vestari.ui.Route
import app.vestari.ui.Tab
import app.vestari.ui.theme.AppTheme

/** Шторка «Ваши аккаунты + Папки», как на макете. */
@Composable
fun AccountSheet(onDismiss: () -> Unit) {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val accounts by Repository.accounts.collectAsState()
    val settings by Repository.settings.collectAsState()
    val categories by Repository.categories.collectAsState()
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.extra.card,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            SectionTitle("Ваши аккаунты")
            if (accounts.size > 1) {
                AccountRow(null, settings.selectedAccountId == null) {
                    Repository.updateSettings { it.copy(selectedAccountId = null) }; onDismiss()
                }
            }
            accounts.forEach { acc ->
                AccountRow(acc, settings.selectedAccountId == acc.id || (accounts.size == 1)) {
                    Repository.updateSettings { it.copy(selectedAccountId = acc.id) }; onDismiss()
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppTheme.extra.accentSoft)
                    .clickable { onDismiss(); nav.push(Route.AddAccount) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text("Добавить аккаунт", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SectionTitle("Папки")
            Text(
                "Удерживайте ≡ и тяните, чтобы поменять порядок",
                style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted,
            )
            Spacer(Modifier.height(4.dp))
            ReorderableFolders(categories) { c ->
                nav.folder = c.id; nav.tab = Tab.Mail; onDismiss()
            }
            SoftRow(Icons.Rounded.Add, "Создать папку", tint = MaterialTheme.colorScheme.primary) { creating = true }
        }
    }

    if (creating) {
        FolderDialog(title = "Новая папка", onDismiss = { creating = false }) { name, icon ->
            creating = false
            val c = Repository.addCategory(name, icon)
            toasts.show("Папка создана", "Отменить", onAction = { Repository.removeCategory(c.id) })
        }
    }
}

@Composable
private fun AccountRow(acc: Account?, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) AppTheme.extra.accentSoft else Color.Transparent, label = "accBg")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (acc == null) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AllInbox, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        } else AccountAvatar(acc, 40.dp)
        Spacer(Modifier.width(16.dp))
        Text(acc?.email ?: "Все ящики", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioCheck(selected)
    }
}

/** Круглая галочка выбора с пружинной анимацией. */
@Composable
fun RadioCheck(selected: Boolean) {
    val scale by animateFloatAsState(if (selected) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.5f), label = "check")
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (selected) Color.Transparent else AppTheme.extra.muted, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp)) }
    }
}

/** Список папок с перетаскиванием за «≡». */
@Composable
private fun ReorderableFolders(categories: List<Category>, onOpen: (Category) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val rowHeight = 56.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }

    Column {
        categories.forEach { c ->
            key(c.id) {
                val dragging = draggingId == c.id
                val elevation by animateDpAsState(if (dragging) 10.dp else 0.dp, label = "lift")
                Row(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) offset else 0f
                            val s = if (dragging) 1.03f else 1f
                            scaleX = s; scaleY = s
                        }
                        .shadow(elevation, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (dragging) AppTheme.extra.card else Color.Transparent)
                        .fillMaxWidth()
                        .height(rowHeight)
                        .clickable { onOpen(c) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(iconFor(c.icon), null, modifier = Modifier.size(26.dp))
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Rounded.DragHandle, "Перетащить", tint = AppTheme.extra.muted,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                            .pointerInput(c.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = c.id; offset = 0f
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = { draggingId = null; offset = 0f },
                                    onDragCancel = { draggingId = null; offset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    val list = Repository.categories.value
                                    val i = list.indexOfFirst { it.id == c.id }
                                    if (offset > rowPx / 2 && i < list.lastIndex) {
                                        Repository.moveCategory(i, i + 1); offset -= rowPx
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else if (offset < -rowPx / 2 && i > 0) {
                                        Repository.moveCategory(i, i - 1); offset += rowPx
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}

/** Выбор папки для письма. */
@Composable
fun MoveToFolderSheet(message: MailMessage, onDismiss: () -> Unit) {
    val toasts = LocalToasts.current
    val categories by Repository.categories.collectAsState()
    val assignments by Repository.assignments.collectAsState()
    val current = assignments[message.key]?.takeIf { it.isNotEmpty() }
        ?: message.autoCategory.takeIf { assignments[message.key] == null }
    var creating by remember { mutableStateOf(false) }

    fun pick(id: String?, name: String) {
        Repository.assign(message, id)
        onDismiss()
        val where = if (id == null) "Убрано из папок" else "Перенесено в «$name»"
        // Умная функция: запоминаем выбор для отправителя и перераспределяем остальные его письма.
        val hadRule = Repository.rules.value.firstOrNull { it.pattern == message.fromEmail.lowercase() }
        Repository.learnFromMove(message, id) { moved ->
            val sender = message.fromName.ifBlank { message.fromEmail }
            toasts.show(
                if (moved > 0) "$where. Ещё $moved от «$sender» — туда же" else where,
                "Отменить",
                onAction = {
                    Repository.assign(message, current)
                    if (Repository.settings.value.learnFromMoves) {
                        if (hadRule != null) Repository.addRule(hadRule.pattern, hadRule.categoryId.ifEmpty { null })
                        else Repository.removeRule(message.fromEmail.lowercase())
                        Repository.redistributeAsync()
                    }
                },
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.extra.card,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            SectionTitle("Переместить в папку")
            Text(message.subject, style = MaterialTheme.typography.bodyMedium, color = AppTheme.extra.muted, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            categories.forEach { c ->
                SoftRow(iconFor(c.icon), c.name, trailing = { if (c.id == current) RadioCheck(true) }) { pick(c.id, c.name) }
            }
            SoftRow(Icons.Outlined.FolderOff, "Без папки", trailing = { if (current == null) RadioCheck(true) }) { pick(null, "") }
            SoftRow(Icons.Rounded.Add, "Новая папка", tint = MaterialTheme.colorScheme.primary) { creating = true }
        }
    }
    if (creating) {
        FolderDialog("Новая папка", onDismiss = { creating = false }) { name, icon ->
            creating = false
            val c = Repository.addCategory(name, icon)
            pick(c.id, c.name)
        }
    }
}

/** Диалог создания/переименования папки с выбором иконки. */
@Composable
fun FolderDialog(
    title: String,
    initialName: String = "",
    initialIcon: String = "folder",
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.extra.card,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it.take(30) }, singleLine = true,
                    label = { Text("Название") }, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FolderIcons.forEach { (k, v) ->
                        val sel = k == icon
                        val bg by animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else AppTheme.extra.accentSoft, label = "ic")
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg).bouncyClick { icon = k },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(v, null, tint = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, icon) }, enabled = name.isNotBlank()) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
