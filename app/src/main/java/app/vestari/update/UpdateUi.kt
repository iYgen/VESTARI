package app.vestari.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vestari.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** Всплывающая карточка «Доступна новая версия» над нижним меню. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val state by Updater.state.collectAsState()
    val visible = state is Updater.State.Available || state is Updater.State.Downloading || state is Updater.State.Ready ||
        (state as? Updater.State.Failed)?.message?.startsWith("Загрузка") == true
    AnimatedVisibility(
        visible, modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.8f)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp), color = AppTheme.extra.card, shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) { UpdateContent(state, compact = true) }
    }
}

/** Блок «Обновления» в профиле. */
@Composable
fun UpdateCard() {
    val state by Updater.state.collectAsState()
    UpdateContent(state, compact = false)
}

@Composable
private fun UpdateContent(state: Updater.State, compact: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(if (compact) 16.dp else 0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                val title = when (state) {
                    is Updater.State.Available -> "Доступна версия ${state.release.version}"
                    is Updater.State.Downloading -> "Загружаю ${state.release.version}…"
                    is Updater.State.Ready -> "Версия ${state.release.version} загружена"
                    else -> "Обновления"
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
                val sub = when (state) {
                    Updater.State.Checking -> "Проверяю…"
                    Updater.State.UpToDate -> "У вас последняя версия ${Updater.currentVersion}"
                    is Updater.State.Failed -> state.message
                    is Updater.State.Ready -> "Нажмите «Установить» — откроется установщик Android"
                    else -> if (Updater.configured) "Установлена версия ${Updater.currentVersion}"
                    else "Версия ${Updater.currentVersion}. Источник обновлений не настроен"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
            }
            if (compact && state !is Updater.State.Downloading) {
                IconButton(onClick = { Updater.dismiss() }) { Icon(Icons.Rounded.Close, "Позже", tint = AppTheme.extra.muted) }
            }
        }

        if (state is Updater.State.Available && state.release.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                state.release.notes, style = MaterialTheme.typography.bodyMedium,
                maxLines = if (compact) 4 else 12, overflow = TextOverflow.Ellipsis,
            )
        }
        if (state is Updater.State.Downloading) {
            val p by animateFloatAsState(state.progress, label = "dl")
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(6.dp))
        }

        Spacer(Modifier.height(12.dp))
        when (state) {
            is Updater.State.Available -> Button(
                onClick = { Updater.startDownload(context, state.release) },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            ) { Text("Скачать обновление") }
            is Updater.State.Ready -> Button(
                onClick = { Updater.install(context, state.file) },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            ) { Text("Установить") }
            is Updater.State.Downloading -> {}
            else -> if (!compact && Updater.configured || state is Updater.State.Failed) {
                TextButton(onClick = { scope.launch { Updater.check() } }) { Text("Проверить обновления") }
            }
        }
    }
}
