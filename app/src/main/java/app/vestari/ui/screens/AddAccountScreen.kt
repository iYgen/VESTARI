@file:OptIn(ExperimentalLayoutApi::class)

package app.vestari.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.vestari.data.Providers
import app.vestari.data.SecurityOptions
import app.vestari.data.imapMode
import app.vestari.data.securityForPort
import app.vestari.data.smtpMode
import app.vestari.data.Repository
import app.vestari.ui.LocalNav
import app.vestari.ui.LocalToasts
import app.vestari.ui.components.FolderChip
import app.vestari.ui.theme.AppTheme
import kotlinx.coroutines.launch


/**
 * Подключение нового ящика, а с [editId] — правка уже подключённого: серверы, порты, шифрование, пароль.
 */
@Composable
fun AddAccountScreen(editId: String? = null) {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val editing = remember(editId) { editId?.let { id -> Repository.accounts.value.firstOrNull { it.id == id } } }

    var provider by remember { mutableStateOf(Providers.byId(editing?.provider ?: "mailru")) }
    var providerTouched by remember { mutableStateOf(editing != null) }
    var email by remember { mutableStateOf(editing?.email ?: "") }
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(editing != null) }
    var imapHost by remember { mutableStateOf(editing?.imapHost ?: provider.imapHost) }
    var imapPort by remember { mutableStateOf((editing?.imapPort ?: provider.imapPort).toString()) }
    var imapSecurity by remember { mutableStateOf(editing?.imapMode ?: "ssl") }
    var smtpHost by remember { mutableStateOf(editing?.smtpHost ?: provider.smtpHost) }
    var smtpPort by remember { mutableStateOf((editing?.smtpPort ?: provider.smtpPort).toString()) }
    var smtpSecurity by remember { mutableStateOf(editing?.smtpMode ?: (securityForPort(provider.smtpPort) ?: "ssl")) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun applyProvider(id: String) {
        provider = Providers.byId(id)
        imapHost = provider.imapHost; imapPort = provider.imapPort.toString()
        smtpHost = provider.smtpHost; smtpPort = provider.smtpPort.toString()
        imapSecurity = securityForPort(provider.imapPort) ?: "ssl"
        smtpSecurity = securityForPort(provider.smtpPort) ?: "ssl"
        if (provider.id == "custom") advanced = true
    }

    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = AppTheme.extra.card, unfocusedContainerColor = AppTheme.extra.card,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    )

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            Text(if (editing != null) "Настройки ящика" else "Новый ящик", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (editing == null) {
                Text("Почтовый сервис", style = MaterialTheme.typography.labelLarge, color = AppTheme.extra.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Providers.all.forEach { p ->
                        FolderChip(p.title, provider.id == p.id) { providerTouched = true; applyProvider(p.id) }
                    }
                }
            }

            OutlinedTextField(
                email,
                {
                    email = it.trim()
                    if (!providerTouched && it.contains('@')) {
                        val guess = Providers.guess(it)
                        if (guess.id != provider.id && guess.id != "custom") applyProvider(guess.id)
                    }
                },
                label = { Text("Адрес почты") }, singleLine = true, enabled = editing == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(18.dp), colors = colors, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password, { password = it },
                label = { Text(if (editing != null) "Новый пароль (пусто — прежний)" else "Пароль для приложения") }, singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Показать пароль")
                    }
                },
                shape = RoundedCornerShape(18.dp), colors = colors, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                name, { name = it }, label = { Text("Ваше имя в письмах (необязательно)") }, singleLine = true,
                shape = RoundedCornerShape(18.dp), colors = colors, modifier = Modifier.fillMaxWidth(),
            )

            if (editing == null) {
                Surface(shape = RoundedCornerShape(20.dp), color = AppTheme.extra.accentSoft) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(provider.hint, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            TextButton(onClick = { advanced = !advanced }) {
                Text(if (advanced) "Скрыть настройки серверов" else "Настройки серверов")
            }
            AnimatedVisibility(advanced, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Входящая почта (IMAP)", style = MaterialTheme.typography.labelLarge, color = AppTheme.extra.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(imapHost, { imapHost = it.trim() }, label = { Text("Сервер") }, singleLine = true,
                            shape = RoundedCornerShape(18.dp), colors = colors, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            imapPort,
                            { v -> imapPort = v.filter(Char::isDigit); v.toIntOrNull()?.let(::securityForPort)?.let { imapSecurity = it } },
                            label = { Text("Порт") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(18.dp), colors = colors, modifier = Modifier.width(96.dp),
                        )
                    }
                    SecurityPicker(imapSecurity) { imapSecurity = it }

                    Spacer(Modifier.height(4.dp))
                    Text("Исходящая почта (SMTP)", style = MaterialTheme.typography.labelLarge, color = AppTheme.extra.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(smtpHost, { smtpHost = it.trim() }, label = { Text("Сервер") }, singleLine = true,
                            shape = RoundedCornerShape(18.dp), colors = colors, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            smtpPort,
                            { v -> smtpPort = v.filter(Char::isDigit); v.toIntOrNull()?.let(::securityForPort)?.let { smtpSecurity = it } },
                            label = { Text("Порт") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(18.dp), colors = colors, modifier = Modifier.width(96.dp),
                        )
                    }
                    SecurityPicker(smtpSecurity) { smtpSecurity = it }
                    Text(
                        "SSL/TLS — шифрование сразу при подключении (его ещё называют «SSL» или «Implicit TLS»). " +
                            "STARTTLS — подключение начинается открыто и затем шифруется. Выберите то, что указано в инструкции вашей почты.",
                        style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted,
                    )
                }
            }

            AnimatedVisibility(error != null) {
                Text(error.orEmpty(), color = AppTheme.extra.unreadDot)
            }

            Button(
                onClick = {
                    error = null; busy = true
                    scope.launch {
                        runCatching {
                            if (editing != null) {
                                Repository.editAccount(
                                    editing.copy(
                                        name = name.trim(),
                                        imapHost = imapHost.trim(), imapPort = imapPort.toIntOrNull() ?: editing.imapPort, imapSecurity = imapSecurity,
                                        smtpHost = smtpHost.trim(), smtpPort = smtpPort.toIntOrNull() ?: editing.smtpPort, smtpSecurity = smtpSecurity,
                                    ),
                                    password,
                                )
                                editing.email
                            } else {
                                Repository.addAccount(
                                    email, name, password, provider,
                                    imapHost, imapPort.toIntOrNull() ?: 993, smtpHost, smtpPort.toIntOrNull() ?: 465,
                                    imapSecurity, smtpSecurity,
                                ).email
                            }
                        }.onSuccess { mail ->
                            toasts.show(if (editing != null) "Настройки $mail сохранены" else "Ящик $mail подключён")
                            nav.pop()
                        }.onFailure { error = it.message ?: "Не удалось подключиться" }
                        busy = false
                    }
                },
                enabled = !busy && email.contains('@') && (editing != null || password.isNotBlank()) &&
                    imapHost.isNotBlank() && smtpHost.isNotBlank(),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                    Text("Проверяю подключение…")
                } else Text(if (editing != null) "Проверить и сохранить" else "Подключить")
            }
            Text(
                "Пароль хранится только на этом телефоне в зашифрованном виде. Письма загружаются напрямую с сервера почты — без посредников.",
                style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted,
            )
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

@Composable
private fun SecurityPicker(value: String, onChange: (String) -> Unit) {
    Segmented(SecurityOptions.map { it.first }, SecurityOptions.indexOfFirst { it.second == value }.coerceAtLeast(0)) { i ->
        onChange(SecurityOptions[i].second)
    }
}
