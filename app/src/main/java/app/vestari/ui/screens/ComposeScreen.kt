package app.vestari.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.vestari.data.Repository
import app.vestari.ui.LocalNav
import app.vestari.ui.LocalToasts
import app.vestari.ui.components.FolderChip
import app.vestari.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun ComposeScreen(replyToKey: String?, initialTo: String) {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val accounts by Repository.accounts.collectAsState()
    val settings by Repository.settings.collectAsState()
    val reply = remember(replyToKey) { replyToKey?.let(Repository::find) }

    var fromId by remember { mutableStateOf(reply?.accountId ?: settings.selectedAccountId ?: accounts.firstOrNull()?.id) }
    var to by remember { mutableStateOf(reply?.fromEmail ?: initialTo) }
    var subject by remember {
        mutableStateOf(reply?.subject?.let { if (it.startsWith("Re:", true)) it else "Re: $it" } ?: "")
    }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    // Цитата исходного письма подгружается в фоне.
    LaunchedEffect(reply?.key) {
        reply ?: return@LaunchedEffect
        val b = runCatching { Repository.loadBody(reply) }.getOrNull() ?: return@LaunchedEffect
        if (text.isBlank()) text = "\n\n${reply.senderTitle} писал(а):\n" + quoteFor(b.text, b.html)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = AppTheme.extra.card, unfocusedContainerColor = AppTheme.extra.card,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    )

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.pop() }) { Icon(Icons.Rounded.Close, "Закрыть") }
            Text(if (reply != null) "Ответ" else "Новое письмо", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    val acc = fromId ?: return@Button toasts.show("Сначала добавьте ящик", error = true)
                    sending = true
                    scope.launch {
                        runCatching { Repository.send(acc, to, subject, text, reply?.messageId) }
                            .onSuccess { toasts.show("Письмо отправлено"); nav.pop() }
                            .onFailure { toasts.show("Не отправлено: ${it.message}", error = true) }
                        sending = false
                    }
                },
                enabled = !sending && to.contains('@'),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp))
                Text("  Отправить")
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (accounts.size > 1) {
                Text("От кого", style = MaterialTheme.typography.labelLarge, color = AppTheme.extra.muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.forEach { a -> FolderChip(a.email, fromId == a.id) { fromId = a.id } }
                }
            }
            OutlinedTextField(
                to, { to = it }, label = { Text("Кому") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(18.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                subject, { subject = it }, label = { Text("Тема") }, singleLine = true,
                shape = RoundedCornerShape(18.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                text, { text = it }, label = { Text("Текст письма") },
                shape = RoundedCornerShape(18.dp), colors = fieldColors,
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
            )
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}
