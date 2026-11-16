@file:OptIn(ExperimentalLayoutApi::class)

package app.vestari.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContactMail
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vestari.data.Account
import app.vestari.data.Contact
import app.vestari.data.Draft
import app.vestari.data.DraftAttachment
import app.vestari.data.Repository
import app.vestari.data.TextAssist
import app.vestari.ui.LocalNav
import app.vestari.ui.LocalToasts
import app.vestari.ui.Route
import app.vestari.ui.components.AccountAvatar
import app.vestari.ui.theme.AppTheme
import app.vestari.ui.theme.AvatarColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Поле редактора, в котором сейчас фокус: от него зависят подсказки адресов. */
private enum class Field { To, Cc, Bcc, Subject, Body }

/** Результат помощника, ожидающий решения: принять или отменить. */
private data class AssistProposal(
    val title: String,
    val result: TextAssist.Result,
    val range: TextRange,
)

private val ru = Locale("ru")

/**
 * Экран нового письма: плотный построчный редактор (как в почтовых клиентах), без карточек.
 * Строки «Кому», «Копия», «Скр. копия», «Тема» разделены тонкими линиями, ниже — текст на всю высоту.
 * Черновик сохраняется сам во время набора; при закрытии с изменениями — короткое подтверждение.
 */
@Composable
fun ComposeScreen(replyToKey: String?, initialTo: String, draftId: String?) {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by Repository.accounts.collectAsState()
    val settings by Repository.settings.collectAsState()

    val existing = remember(draftId) { draftId?.let(Repository::draft) }
    val reply = remember(replyToKey) { (replyToKey ?: existing?.replyToKey)?.let(Repository::find) }
    val id = remember { existing?.id ?: UUID.randomUUID().toString() }

    var fromId by remember {
        mutableStateOf(
            existing?.accountId ?: reply?.accountId
                ?: settings.selectedAccountId?.takeIf { s -> accounts.any { it.id == s } } ?: accounts.firstOrNull()?.id,
        )
    }
    val from: Account? = accounts.firstOrNull { it.id == fromId }

    val to = remember { mutableStateListOf<String>().apply { addAll(existing?.to ?: listOfNotNull((reply?.fromEmail ?: initialTo).ifBlank { null })) } }
    val cc = remember { mutableStateListOf<String>().apply { addAll(existing?.cc.orEmpty()) } }
    val bcc = remember { mutableStateListOf<String>().apply { addAll(existing?.bcc.orEmpty()) } }
    var toInput by remember { mutableStateOf("") }
    var ccInput by remember { mutableStateOf("") }
    var bccInput by remember { mutableStateOf("") }
    var showCopies by remember { mutableStateOf(cc.isNotEmpty() || bcc.isNotEmpty()) }
    var subject by remember {
        mutableStateOf(existing?.subject ?: reply?.subject?.let { if (it.startsWith("Re:", true)) it else "Re: $it" } ?: "")
    }
    var body by remember { mutableStateOf(TextFieldValue(existing?.body ?: "")) }
    val attachments = remember { mutableStateListOf<DraftAttachment>().apply { addAll(existing?.attachments.orEmpty()) } }

    var focused by remember { mutableStateOf<Field?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var savedAt by remember { mutableStateOf(existing?.updated?.takeIf { it > 0 }) }
    var proposal by remember { mutableStateOf<AssistProposal?>(null) }
    var editingSignature by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }

    val toFocus = remember { FocusRequester() }
    val ccFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }

    // Подсказки адресов собираются из скачанной почты — в фоне, чтобы экран открылся мгновенно.
    LaunchedEffect(Unit) { contacts = withContext(Dispatchers.Default) { Repository.contacts() } }

    // Открыли запланированное письмо — значит, его хотят поправить: отправку по расписанию снимаем.
    LaunchedEffect(Unit) {
        if (existing?.sendAt != null) {
            Repository.cancelSchedule(context, existing.id)
            toasts.show("Отложенная отправка отменена — письмо снова черновик")
        }
    }

    // Новое письмо — сразу курсор в «Кому», ответ — в текст.
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { if (to.isEmpty()) toFocus.requestFocus() else bodyFocus.requestFocus() }
    }

    // Цитата исходного письма при ответе подгружается в фоне.
    LaunchedEffect(reply?.key) {
        if (reply == null || existing != null) return@LaunchedEffect
        val b = runCatching { Repository.loadBody(reply) }.getOrNull() ?: return@LaunchedEffect
        if (body.text.isBlank()) {
            body = TextFieldValue("\n\n${reply.senderTitle} писал(а):\n" + quoteFor(b.text, b.html), TextRange(0))
        }
    }

    /** Разбирает набранный текст на адреса: готовые уходят в чипы, остаток остаётся в поле. */
    fun commit(list: SnapshotStateList<String>, input: String, force: Boolean): String {
        val parts = input.split(',', ';', ' ', '\n')
        val done = if (force) parts else parts.dropLast(1)
        done.map { it.trim().trim('<', '>') }.filter { it.isNotEmpty() }.forEach { a ->
            if (list.none { it.equals(a, true) }) list += a
        }
        return if (force) "" else parts.last()
    }

    fun commitAll() {
        toInput = commit(to, toInput, true)
        ccInput = commit(cc, ccInput, true)
        bccInput = commit(bcc, bccInput, true)
    }

    fun current(): Draft = Draft(
        id = id, accountId = fromId,
        to = to.toList() + toInput.trim().takeIf { it.contains('@') }.let(::listOfNotNull),
        cc = cc.toList() + ccInput.trim().takeIf { it.contains('@') }.let(::listOfNotNull),
        bcc = bcc.toList() + bccInput.trim().takeIf { it.contains('@') }.let(::listOfNotNull),
        subject = subject, body = body.text, attachments = attachments.toList(),
        replyToKey = reply?.key, inReplyTo = reply?.messageId,
    )

    // Автосохранение черновика: через секунду после паузы в наборе, без диалогов.
    LaunchedEffect(Unit) {
        snapshotFlow { if (dirty) current() else null }.collectLatest { d ->
            if (d == null || sending) return@collectLatest
            delay(1200)
            Repository.saveDraft(d)
            if (!d.isEmpty) savedAt = System.currentTimeMillis()
        }
    }

    fun close() {
        if (sending) return nav.pop().let { }
        commitAll()
        val d = current()
        when {
            !dirty -> if (existing == null) Repository.deleteDraft(id)
            d.isEmpty -> Repository.deleteDraft(id)
            else -> { Repository.saveDraft(d); toasts.show("Черновик сохранён") }
        }
        nav.pop()
    }
    // Системная «Назад»: первое нажатие закрывает клавиатуру (это делает сама система), второе — редактор.
    BackHandler { close() }

    /** Проверки перед отправкой. Возвращает черновик или null (и показывает, что не так). */
    fun validated(): Draft? {
        commitAll()
        val d = current()
        if (d.accountId == null) { toasts.show("Сначала добавьте почтовый ящик", error = true); return null }
        val all = d.to + d.cc + d.bcc
        if (all.isEmpty()) {
            toasts.show("Укажите адресата", error = true)
            runCatching { toFocus.requestFocus() }
            return null
        }
        all.firstOrNull { !TextAssist.isEmail(it) }?.let {
            toasts.show("Проверьте адрес: $it", error = true); return null
        }
        if (d.attachments.sumOf { it.size } > 25L * 1024 * 1024) {
            toasts.show("Вложения больше 25 МБ — почта может не принять письмо", error = true)
        }
        return d
    }

    fun send(confirmedNoSubject: Boolean = false) {
        val d = validated() ?: return
        if (d.subject.isBlank() && !confirmedNoSubject) {
            toasts.show("Письмо без темы", "Отправить") { send(confirmedNoSubject = true) }
            return
        }
        sending = true
        Repository.saveDraft(d) // если связь пропадёт — письмо останется в черновиках
        Repository.sendInBackground(d) { e ->
            sending = false
            if (e == null) {
                toasts.show("Письмо отправлено")
                if (nav.current is Route.Compose) nav.pop()
            } else {
                toasts.show("Не отправлено: ${e.message ?: "ошибка"}. Письмо сохранено в черновиках", error = true)
            }
        }
    }

    fun schedule(at: Long, label: String) {
        val d = validated() ?: return
        Repository.scheduleDraft(context, d, at)
        toasts.show("Письмо уйдёт $label")
        nav.pop()
    }

    // Вложения: стандартный системный выбор файлов и фото.
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            scope.launch {
                runCatching { Repository.importAttachment(context, id, uri) }
                    .onSuccess { attachments += it; dirty = true }
                    .onFailure { toasts.show("Не удалось добавить файл: ${it.message}", error = true) }
            }
        }
    }
    // Адрес из контактов телефона: выбор конкретного e-mail даёт доступ только к нему, без разрешения на все контакты.
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()?.let { email ->
            val target = when (focused) { Field.Cc -> cc; Field.Bcc -> bcc; else -> to }
            if (target.none { it.equals(email, true) }) target += email
            dirty = true
        }
    }

    fun runAssist(title: String, action: (String) -> TextAssist.Result) {
        val sel = body.selection
        val range = if (sel.collapsed) TextRange(0, body.text.length) else TextRange(sel.min, sel.max)
        val source = body.text.substring(range.min, range.max)
        if (source.isBlank()) { toasts.show("Сначала напишите текст письма"); return }
        val r = action(source)
        if (r.text == source) { toasts.show("Править нечего — текст в порядке"); return }
        proposal = AssistProposal(title, r, range)
    }

    val bg = AppTheme.extra.card
    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (AppTheme.extra.isDark) 0.6f else 0.45f)

    Surface(Modifier.fillMaxSize(), color = bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
            // ---------- верхняя панель ----------
            TopBar(
                from = from, accounts = accounts, sending = sending,
                canSend = to.isNotEmpty() || toInput.contains('@') || cc.isNotEmpty() || bcc.isNotEmpty(),
                onClose = ::close,
                onPickAccount = { fromId = it.id; dirty = true },
                onSend = { send() },
            )
            if (sending) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            else HorizontalDivider(color = divider)

            // ---------- поля и текст ----------
            val scroll = rememberScrollState()
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                RecipientRow(
                    label = "Кому:", list = to, input = toInput,
                    onInput = { toInput = commit(to, it, false); dirty = true },
                    onCommit = { toInput = commit(to, toInput, true) },
                    onRemove = { to.remove(it); dirty = true },
                    onFocus = { if (it) focused = Field.To else { toInput = commit(to, toInput, true) } },
                    focusRequester = toFocus, contacts = contacts,
                    trailing = {
                        SmallIcon(Icons.Outlined.ContactMail, "Адрес из контактов") {
                            runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI)) }
                                .onFailure { toasts.show("Не нашлось приложения контактов") }
                        }
                    },
                )
                Suggestions(focused == Field.To, toInput, to + cc + bcc, contacts) { c ->
                    to += c.email; toInput = ""; dirty = true
                }
                HorizontalDivider(color = divider)

                AnimatedVisibility(!showCopies, enter = fadeIn(), exit = fadeOut() + shrinkVertically()) {
                    Column {
                        Text(
                            "Копия / Скр. копия",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showCopies = true
                                    scope.launch { delay(120); runCatching { ccFocus.requestFocus() } }
                                }
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                        )
                        HorizontalDivider(color = divider)
                    }
                }
                AnimatedVisibility(showCopies, enter = expandVertically(tween(220)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        RecipientRow(
                            label = "Копия:", list = cc, input = ccInput,
                            onInput = { ccInput = commit(cc, it, false); dirty = true },
                            onCommit = { ccInput = commit(cc, ccInput, true) },
                            onRemove = { cc.remove(it); dirty = true },
                            onFocus = { if (it) focused = Field.Cc else { ccInput = commit(cc, ccInput, true) } },
                            focusRequester = ccFocus, contacts = contacts,
                        )
                        Suggestions(focused == Field.Cc, ccInput, to + cc + bcc, contacts) { c -> cc += c.email; ccInput = ""; dirty = true }
                        HorizontalDivider(color = divider)
                        RecipientRow(
                            label = "Скр. копия:", list = bcc, input = bccInput,
                            onInput = { bccInput = commit(bcc, it, false); dirty = true },
                            onCommit = { bccInput = commit(bcc, bccInput, true) },
                            onRemove = { bcc.remove(it); dirty = true },
                            onFocus = { if (it) focused = Field.Bcc else { bccInput = commit(bcc, bccInput, true) } },
                            focusRequester = remember { FocusRequester() }, contacts = contacts,
                        )
                        Suggestions(focused == Field.Bcc, bccInput, to + cc + bcc, contacts) { c -> bcc += c.email; bccInput = ""; dirty = true }
                        HorizontalDivider(color = divider)
                    }
                }

                SubjectRow(
                    subject = subject,
                    onChange = { subject = it; dirty = true },
                    onFocus = { if (it) focused = Field.Subject },
                    onNext = { runCatching { bodyFocus.requestFocus() } },
                    onAttach = { runCatching { pickFiles.launch("*/*") } },
                    onSchedule = ::schedule,
                    onSaveDraft = {
                        commitAll()
                        val d = current()
                        if (d.isEmpty) toasts.show("Пустое письмо сохранять не нужно")
                        else { Repository.saveDraft(d); savedAt = System.currentTimeMillis(); dirty = true; toasts.show("Черновик сохранён") }
                    },
                    onSignature = { editingSignature = true },
                    signatureEnabled = from != null,
                    onDiscard = {
                        Repository.deleteDraft(id)
                        dirty = false
                        nav.pop()
                        toasts.show("Черновик удалён")
                    },
                )
                HorizontalDivider(color = divider)

                if (attachments.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        attachments.forEach { a ->
                            AttachmentRow(a) {
                                attachments.remove(a); dirty = true
                                scope.launch(Dispatchers.IO) { java.io.File(a.path).delete() }
                            }
                        }
                    }
                    HorizontalDivider(color = divider)
                }

                // Текст письма: без рамки, занимает всё свободное место; нажатие на пустое место ставит курсор.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            runCatching { bodyFocus.requestFocus() }
                        },
                ) {
                    BasicTextField(
                        value = body,
                        onValueChange = { if (it.text != body.text) dirty = true; body = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .focusRequester(bodyFocus)
                            .onFocusChanged { if (it.isFocused) focused = Field.Body },
                        decorationBox = { inner ->
                            if (body.text.isEmpty()) {
                                Text("Текст письма", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = AppTheme.extra.muted)
                            }
                            inner()
                        },
                    )
                }
                if (from != null && from.signature.isNotBlank()) {
                    Text(
                        "--\n" + from.signature.trim(),
                        style = MaterialTheme.typography.bodyMedium, color = AppTheme.extra.muted,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 24.dp)
                            .clip(RoundedCornerShape(8.dp)).clickable { editingSignature = true },
                    )
                }
            }

            // ---------- результат помощника ----------
            AnimatedVisibility(
                proposal != null,
                enter = expandVertically(tween(200)) + fadeIn(), exit = shrinkVertically(tween(160)) + fadeOut(),
            ) {
                proposal?.let { p ->
                    AssistPanel(
                        p,
                        onCancel = { proposal = null },
                        onAccept = {
                            val before = body
                            val newText = body.text.replaceRange(p.range.min, p.range.max, p.result.text)
                            body = TextFieldValue(newText, TextRange(p.range.min + p.result.text.length))
                            dirty = true
                            proposal = null
                            toasts.show("Изменения применены", "Отменить") { body = before }
                        },
                    )
                }
            }

            // ---------- нижняя строка: помощник и статус черновика ----------
            HorizontalDivider(color = divider)
            BottomBar(
                savedAt = savedAt,
                hasSelection = !body.selection.collapsed,
                onAssist = { which ->
                    when (which) {
                        0 -> runAssist("Исправить ошибки", TextAssist::fixErrors)
                        1 -> runAssist("Сделать короче", TextAssist::shorten)
                        2 -> runAssist("Тон: официальнее") { TextAssist.changeTone(it, TextAssist.Tone.Formal) }
                        3 -> runAssist("Тон: дружелюбнее") { TextAssist.changeTone(it, TextAssist.Tone.Friendly) }
                    }
                },
                onAttach = { runCatching { pickFiles.launch("*/*") } },
            )
        }
    }

    if (editingSignature && from != null) {
        SignatureDialog(from, onDismiss = { editingSignature = false }) { sig ->
            Repository.updateAccount(from.copy(signature = sig))
            editingSignature = false
            toasts.show(if (sig.isBlank()) "Подпись убрана" else "Подпись сохранена")
        }
    }
}

// ============================ части экрана ============================

@Composable
private fun TopBar(
    from: Account?, accounts: List<Account>, sending: Boolean, canSend: Boolean,
    onClose: () -> Unit, onPickAccount: (Account) -> Unit, onSend: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Закрыть") }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = accounts.size > 1) { menu = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    from?.email ?: "Нет ящика",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
                if (accounts.size > 1) Icon(Icons.Rounded.ArrowDropDown, "Сменить ящик", tint = AppTheme.extra.muted, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = AppTheme.extra.card) {
                accounts.forEach { a ->
                    DropdownMenuItem(
                        leadingIcon = { AccountAvatar(a, 26.dp) },
                        text = { Text(a.email, style = MaterialTheme.typography.bodyMedium) },
                        trailingIcon = { if (a.id == from?.id) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onPickAccount(a); menu = false },
                    )
                }
            }
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (sending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else {
                val tint by animateColorAsState(if (canSend) MaterialTheme.colorScheme.primary else AppTheme.extra.muted, label = "sendTint")
                IconButton(onClick = onSend) { Icon(Icons.AutoMirrored.Rounded.Send, "Отправить", tint = tint) }
            }
        }
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, description: String, tint: Color = AppTheme.extra.muted, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Строка адресатов: подпись слева, чипы и поле ввода в одной строке с переносом, справа — действие. */
@Composable
private fun RecipientRow(
    label: String,
    list: SnapshotStateList<String>,
    input: String,
    onInput: (String) -> Unit,
    onCommit: () -> Unit,
    onRemove: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    contacts: List<Contact>,
    trailing: (@Composable () -> Unit)? = null,
) {
    val names = remember(contacts) { contacts.associate { it.email to it.name } }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppTheme.extra.muted)
        Spacer(Modifier.width(8.dp))
        FlowRow(
            Modifier.weight(1f).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            list.forEach { email ->
                RecipientChip(email, names[email.lowercase()].orEmpty(), onRemove = { onRemove(email) })
            }
            BasicTextField(
                value = input,
                onValueChange = onInput,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { onCommit() }),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 120.dp)
                    .align(Alignment.CenterVertically)
                    .padding(vertical = 6.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocus(it.isFocused) },
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun RecipientChip(email: String, name: String, onRemove: () -> Unit) {
    val valid = TextAssist.isEmail(email)
    val color = AvatarColors[(email.lowercase().hashCode() and 0x7fffffff) % AvatarColors.size]
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (valid) AppTheme.extra.accentSoft else AppTheme.extra.unreadDot.copy(alpha = 0.15f))
            .padding(start = 3.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(
                (name.ifBlank { email }).first().uppercaseChar().toString(),
                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            name.ifBlank { email },
            style = MaterialTheme.typography.labelLarge,
            color = if (valid) MaterialTheme.colorScheme.onSurface else AppTheme.extra.unreadDot,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp),
        )
        Box(
            Modifier.padding(start = 2.dp).size(20.dp).clip(CircleShape).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, "Убрать $email", tint = AppTheme.extra.muted, modifier = Modifier.size(14.dp)) }
    }
}

/** Компактный список совпадений под полем адреса: инициалы, имя, адрес. Встраивается в поток, ничего не перекрывает. */
@Composable
private fun Suggestions(visible: Boolean, query: String, added: List<String>, contacts: List<Contact>, onPick: (Contact) -> Unit) {
    val q = query.trim().lowercase()
    val matches = remember(q, contacts, added.size) {
        if (q.isEmpty()) emptyList()
        else contacts.asSequence()
            .filter { c -> added.none { it.equals(c.email, true) } }
            .filter { c -> c.email.contains(q) || c.name.lowercase().split(' ').any { it.startsWith(q) } || c.name.lowercase().startsWith(q) }
            .take(5).toList()
    }
    AnimatedVisibility(visible && matches.isNotEmpty(), enter = expandVertically(tween(160)) + fadeIn(), exit = shrinkVertically(tween(120)) + fadeOut()) {
        Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp)) {
            matches.forEach { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(c) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = AvatarColors[(c.email.hashCode() and 0x7fffffff) % AvatarColors.size]
                    Box(Modifier.size(30.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                        Text(initials(c), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        if (c.name.isNotBlank()) {
                            Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(c.email, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun initials(c: Contact): String {
    val parts = c.name.split(' ', '.', '"').filter { it.isNotBlank() && it.first().isLetter() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        parts.size == 1 -> parts[0].first().uppercase()
        else -> c.email.first().uppercase()
    }
}

/** Строка темы со скрепкой, отложенной отправкой и меню прочих действий. */
@Composable
private fun SubjectRow(
    subject: String,
    onChange: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onNext: () -> Unit,
    onAttach: () -> Unit,
    onSchedule: (Long, String) -> Unit,
    onSaveDraft: () -> Unit,
    onSignature: () -> Unit,
    signatureEnabled: Boolean,
    onDiscard: () -> Unit,
) {
    var scheduleMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Тема:", style = MaterialTheme.typography.bodyMedium, color = AppTheme.extra.muted)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = subject,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            modifier = Modifier.weight(1f).padding(vertical = 12.dp).onFocusChanged { onFocus(it.isFocused) },
        )
        SmallIcon(Icons.Outlined.AttachFile, "Прикрепить файл", onClick = onAttach)
        Box {
            SmallIcon(Icons.Outlined.Schedule, "Отправить позже") { scheduleMenu = true }
            DropdownMenu(scheduleMenu, onDismissRequest = { scheduleMenu = false }, containerColor = AppTheme.extra.card) {
                Text(
                    "Отправить позже", style = MaterialTheme.typography.labelMedium, color = AppTheme.extra.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                scheduleOptions().forEach { (title, at) ->
                    DropdownMenuItem(
                        text = { Text(title, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { scheduleMenu = false; onSchedule(at, scheduleLabel(at)) },
                    )
                }
            }
        }
        Box {
            SmallIcon(Icons.Rounded.MoreVert, "Ещё") { moreMenu = true }
            DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }, containerColor = AppTheme.extra.card) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.Save, null) },
                    text = { Text("Сохранить черновик") },
                    onClick = { moreMenu = false; onSaveDraft() },
                )
                if (signatureEnabled) DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                    text = { Text("Подпись ящика") },
                    onClick = { moreMenu = false; onSignature() },
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = AppTheme.extra.unreadDot) },
                    text = { Text("Удалить черновик", color = AppTheme.extra.unreadDot) },
                    onClick = { moreMenu = false; onDiscard() },
                )
            }
        }
    }
}

/** Варианты отложенной отправки относительно текущего времени. */
private fun scheduleOptions(now: Calendar = Calendar.getInstance()): List<Pair<String, Long>> {
    fun at(daysAhead: Int, hour: Int) = (now.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, daysAhead); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    val list = mutableListOf("Через час" to now.timeInMillis + 3600_000L)
    if (now.get(Calendar.HOUR_OF_DAY) < 18) list += "Сегодня в 19:00" to at(0, 19)
    list += "Завтра в 9:00" to at(1, 9)
    val dow = now.get(Calendar.DAY_OF_WEEK)
    val toMonday = ((Calendar.MONDAY - dow + 7) % 7).let { if (it == 0) 7 else it }
    if (toMonday > 1) list += "В понедельник в 9:00" to at(toMonday, 9)
    return list
}

private fun scheduleLabel(at: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance()
    val time = SimpleDateFormat("HH:mm", ru).format(c.time)
    return when {
        c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> "сегодня в $time"
        c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) + 1 -> "завтра в $time"
        else -> SimpleDateFormat("d MMMM в HH:mm", ru).format(c.time)
    }
}

@Composable
private fun AttachmentRow(a: DraftAttachment, onRemove: () -> Unit) {
    val ext = a.name.substringAfterLast('.', "").uppercase().take(4)
    val (icon, tint) = when {
        a.mime.startsWith("image/") -> Icons.Outlined.Image to Color(0xFF3D86F5)
        a.mime == "application/pdf" || ext == "PDF" -> Icons.Outlined.PictureAsPdf to Color(0xFFE5484D)
        else -> Icons.Outlined.Description to MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(a.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Text(
            listOf(ext.ifEmpty { null }, formatSize(a.size)).filterNotNull().joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted,
        )
        Spacer(Modifier.weight(1f))
        SmallIcon(Icons.Rounded.Close, "Убрать вложение ${a.name}", onClick = onRemove)
    }
}

private fun formatSize(b: Long): String = when {
    b < 1024 -> "$b Б"
    b < 1024 * 1024 -> "${b / 1024} КБ"
    else -> String.format(ru, "%.1f МБ", b / 1024.0 / 1024.0)
}

/** Нижняя строка: кнопка помощника с коротким меню и тихий статус черновика. */
@Composable
private fun BottomBar(savedAt: Long?, hasSelection: Boolean, onAssist: (Int) -> Unit, onAttach: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable { menu = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Помощь", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = AppTheme.extra.card) {
                Text(
                    if (hasSelection) "Для выделенного текста" else "Для всего письма",
                    style = MaterialTheme.typography.labelMedium, color = AppTheme.extra.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                listOf(
                    Triple(0, "Исправить ошибки", Icons.Outlined.Spellcheck),
                    Triple(1, "Сделать короче", Icons.Outlined.ShortText),
                    Triple(2, "Тон: официальнее", Icons.Outlined.TheaterComedy),
                    Triple(3, "Тон: дружелюбнее", Icons.Outlined.TheaterComedy),
                ).forEach { (i, title, icon) ->
                    DropdownMenuItem(
                        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
                        text = { Text(title, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { menu = false; onAssist(i) },
                    )
                }
            }
        }
        SmallIcon(Icons.Outlined.AttachFile, "Прикрепить файл", onClick = onAttach)
        Spacer(Modifier.weight(1f))
        if (savedAt != null) {
            Text(
                "Черновик · " + SimpleDateFormat("HH:mm", ru).format(savedAt),
                style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.muted,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/** Предпросмотр правки помощника: результат можно принять или отменить. */
@Composable
private fun AssistPanel(p: AssistProposal, onCancel: () -> Unit, onAccept: () -> Unit) {
    Surface(
        color = AppTheme.extra.accentSoft,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    p.title + if (p.result.changes > 0) " · правок: ${p.result.changes}" else "",
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                )
            }
            Text(
                p.result.text,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                modifier = Modifier.padding(top = 6.dp, end = 8.dp).heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Отменить") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = onAccept, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("Принять")
                }
            }
        }
    }
}

@Composable
private fun SignatureDialog(acc: Account, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(acc.signature) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.extra.card,
        title = { Text("Подпись для ${acc.email}") },
        text = {
            OutlinedTextField(
                text, { text = it },
                placeholder = { Text("Например:\nИван Петров\n+7 900 000-00-00") },
                minLines = 3, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ============================ черновики ============================

/** Список черновиков и запланированных писем. */
@Composable
fun DraftsScreen() {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val context = LocalContext.current
    val drafts by Repository.drafts.collectAsState()
    val settings by Repository.settings.collectAsState()
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", ru) }

    Surface(Modifier.fillMaxSize(), color = AppTheme.extra.card) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(Modifier.statusBarsPadding().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.pop() }) { Icon(Icons.Rounded.Close, "Закрыть") }
                Text("Черновики", style = titleStyle(settings.titleSize).copy(fontSize = 20.sp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (drafts.isEmpty()) {
                Text(
                    "Черновиков нет. Незаконченные письма сохраняются сюда сами.",
                    color = AppTheme.extra.muted, modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(drafts.sortedWith(compareByDescending<Draft> { it.sendAt != null }.thenByDescending { it.updated }), key = { it.id }) { d ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.push(Route.Compose(draftId = d.id)) }
                            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
                            .animateItem(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (d.to + d.cc).joinToString(", ").ifBlank { "Без адресата" },
                                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                d.subject.ifBlank { "(без темы)" } + if (d.body.isNotBlank()) " — " + d.body.trim().replace('\n', ' ') else "",
                                style = MaterialTheme.typography.bodyMedium, color = AppTheme.extra.muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                if (d.sendAt != null) {
                                    Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Отправится ${scheduleLabel(d.sendAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text(fmt.format(d.updated), style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.muted)
                                }
                                if (d.attachments.isNotEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Outlined.AttachFile, null, tint = AppTheme.extra.muted, modifier = Modifier.size(13.dp))
                                    Text("${d.attachments.size}", style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.muted)
                                }
                            }
                        }
                        IconButton(onClick = {
                            if (d.sendAt != null) app.vestari.sync.ScheduledSendWorker.cancel(context, d.id)
                            Repository.deleteDraft(d.id)
                            toasts.show(if (d.sendAt != null) "Отправка отменена, письмо удалено" else "Черновик удалён")
                        }) { Icon(Icons.Outlined.Delete, "Удалить", tint = AppTheme.extra.muted) }
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}
