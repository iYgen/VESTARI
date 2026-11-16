@file:OptIn(ExperimentalLayoutApi::class)

package app.vestari.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vestari.data.ListStyle
import app.vestari.data.PictureFit
import app.vestari.data.PicturePlace
import app.vestari.data.Repository
import app.vestari.data.Settings
import app.vestari.ui.LocalNav
import app.vestari.ui.LocalToasts
import app.vestari.ui.components.Backdrops
import app.vestari.ui.components.BrandTitle
import app.vestari.ui.components.FolderChip
import app.vestari.ui.components.LookPreview
import app.vestari.ui.components.Pattern
import app.vestari.ui.components.bouncyClick
import app.vestari.ui.theme.AppTheme
import app.vestari.ui.theme.Palettes
import kotlinx.coroutines.launch

/** Готовый вариант оформления: название и что он меняет в настройках. */
private class LookPreset(val title: String, val needsPicture: Boolean = false, val apply: (Settings) -> Settings)

private val lookPresets = listOf(
    LookPreset("Классика") { it.copy(showLogo = true, logoText = false, composeBackdrop = "wave", picturePlace = PicturePlace.Off, cornerStyle = 1) },
    LookPreset("Минимум") { it.copy(showLogo = false, logoText = false, composeBackdrop = Backdrops.NONE, picturePlace = PicturePlace.Off, cornerStyle = 0) },
    LookPreset("Фирменный") { it.copy(showLogo = true, logoText = true, composeBackdrop = "aurora", picturePlace = PicturePlace.Off, cornerStyle = 2) },
    LookPreset("Картинка в шапке", needsPicture = true) {
        it.copy(showLogo = true, composeBackdrop = Backdrops.NONE, picturePlace = PicturePlace.Header, pictureFit = PictureFit.Crop, pictureHeight = 200, pictureVeil = 0.35f)
    },
    LookPreset("Картинка на фоне", needsPicture = true) {
        it.copy(showLogo = true, composeBackdrop = Backdrops.NONE, picturePlace = PicturePlace.Full, pictureFit = PictureFit.Crop, pictureVeil = 0.6f)
    },
    LookPreset("Картинка внизу", needsPicture = true) {
        it.copy(showLogo = true, composeBackdrop = "wave", picturePlace = PicturePlace.Footer, pictureFit = PictureFit.Width, pictureHeight = 220, pictureVeil = 0.3f)
    },
)

/**
 * «Внешний вид»: сверху живой пример, ниже готовые варианты картинками,
 * затем подробные настройки логотипа, узора, своей картинки, цветов и списка писем.
 */
@Composable
fun AppearanceScreen() {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Repository.settings.collectAsState()
    var pendingPreset by remember { mutableStateOf<LookPreset?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val preset = pendingPreset
        pendingPreset = null
        if (uri != null) scope.launch {
            runCatching { Repository.setCustomBackdrop(context, uri) }
                .onSuccess {
                    preset?.let { p -> Repository.updateSettings(p.apply) }
                    toasts.show("Картинка установлена")
                }
                .onFailure { toasts.show("Не удалось открыть картинку", error = true) }
        }
    }
    fun pick(preset: LookPreset? = null) { pendingPreset = preset; runCatching { pickImage.launch("image/*") } }
    fun set(block: (Settings) -> Settings) = Repository.updateSettings(block)

    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.statusBarsPadding().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                BrandTitle("Внешний вид", titleStyle(settings.titleSize))
            }

            // ---- живой пример ----
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(21.dp), shadowElevation = 8.dp, color = Color.Transparent) {
                    LookPreview(settings, width = 150.dp)
                }
            }

            // ---- готовые варианты ----
            Card {
                Text("Готовые варианты", style = MaterialTheme.typography.titleMedium)
                Text("Нажмите на пример — оформление сразу применится", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    lookPresets.forEach { p ->
                        val missing = p.needsPicture && settings.customBackdrop == null
                        val applied = p.apply(settings)
                        PreviewTile(p.title, selected = !missing && applied == settings, onClick = { if (missing) pick(p) else set(p.apply) }) {
                            if (missing) {
                                Box(Modifier.fillMaxSize().background(AppTheme.extra.accentSoft), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                                        Text("картинка", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else LookPreview(applied, width = 78.dp)
                        }
                    }
                }
            }

            // ---- логотип ----
            Card {
                Text("Логотип", style = MaterialTheme.typography.titleMedium)
                SwitchRow("Показывать логотип", "Значок Vestari рядом с заголовком во всех разделах", settings.showLogo) { v -> set { it.copy(showLogo = v) } }
                AnimatedVisibility(settings.showLogo) {
                    SwitchRow("Надпись VESTARI", "Маленькая подпись над заголовком", settings.logoText) { v -> set { it.copy(logoText = v) } }
                }
            }

            // ---- узор фона ----
            Card {
                Text("Узор фона", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Backdrops.presets.forEach { (id, title) ->
                        PreviewTile(title, settings.composeBackdrop == id, small = true, onClick = { set { it.copy(composeBackdrop = id) } }) {
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppTheme.extra.bgTop, AppTheme.extra.bgBottom)))) {
                                Pattern(id, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                if (settings.picturePlace == PicturePlace.Full && settings.customBackdrop != null) {
                    Text(
                        "Сейчас картинка на весь экран закрывает узор",
                        style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // ---- своя картинка ----
            Card {
                Text("Своя картинка", style = MaterialTheme.typography.titleMedium)
                Text("Любое фото из галереи — в шапке, внизу или на весь экран", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { pick() }, shape = RoundedCornerShape(16.dp)) {
                        Text(if (settings.customBackdrop == null) "Выбрать картинку" else "Заменить картинку")
                    }
                    if (settings.customBackdrop != null) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            set { it.copy(customBackdrop = null, picturePlace = PicturePlace.Off) }
                            toasts.show("Картинка убрана")
                        }) { Text("Убрать") }
                    }
                }
                AnimatedVisibility(settings.customBackdrop != null) {
                    Column {
                        Label("Где показывать")
                        ChoiceRow(PicturePlace.entries.map { it.title }, settings.picturePlace.ordinal) { i -> set { it.copy(picturePlace = PicturePlace.entries[i]) } }
                        AnimatedVisibility(settings.picturePlace != PicturePlace.Off) {
                            Column {
                                Label("Как вписать")
                                ChoiceRow(PictureFit.entries.map { it.title }, settings.pictureFit.ordinal) { i -> set { it.copy(pictureFit = PictureFit.entries[i]) } }
                                Label("Какую часть показывать")
                                Segmented(listOf("Верх", "Центр", "Низ"), settings.pictureAlign) { i -> set { it.copy(pictureAlign = i) } }
                                Label("Вуаль поверх картинки · ${(settings.pictureVeil * 100).toInt()}%")
                                Text("Чем больше, тем лучше читается текст", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                                Slider(value = settings.pictureVeil, valueRange = 0f..0.9f, onValueChange = { v -> set { it.copy(pictureVeil = v) } })
                                AnimatedVisibility(settings.picturePlace != PicturePlace.Full) {
                                    Column {
                                        Label("Высота картинки · ${settings.pictureHeight} dp")
                                        Slider(
                                            value = settings.pictureHeight.toFloat(), valueRange = 100f..360f,
                                            onValueChange = { v -> set { it.copy(pictureHeight = v.toInt()) } },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- цвета и тема ----
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
                                    .bouncyClick { set { it.copy(palette = i) } },
                                contentAlignment = Alignment.Center,
                            ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                            Text(p.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Label("Тема")
                Segmented(listOf("Как в системе", "Светлая", "Тёмная"), settings.darkMode) { m -> set { it.copy(darkMode = m) } }
            }

            // ---- список писем ----
            Card {
                Text("Список писем", style = MaterialTheme.typography.titleMedium)
                Label("Вид")
                Segmented(ListStyle.entries.map { it.title }, settings.listStyle.ordinal) { i -> set { it.copy(listStyle = ListStyle.entries[i]) } }
                Text(
                    settings.listStyle.hint, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                )
                Label("Углы карточек")
                Segmented(listOf("Острые", "Обычные", "Круглые"), settings.cornerStyle) { i -> set { it.copy(cornerStyle = i) } }
                Label("Размер заголовков")
                Segmented(listOf("Маленький", "Средний", "Крупный"), settings.titleSize) { s -> set { it.copy(titleSize = s) } }
                Spacer(Modifier.height(8.dp))
                SwitchRow("Превью текста письма", "Первые строки письма под темой", settings.showPreview) { v -> set { it.copy(showPreview = v) } }
            }
            Spacer(Modifier.navigationBarsPadding().height(40.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
}

/** Выбор из нескольких вариантов чипами (переносятся на новую строку). */
@Composable
private fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o -> FolderChip(o, i == selected) { onSelect(i) } }
    }
}

/** Плитка-пример для выбора: картинка, галочка у выбранной, подпись. */
@Composable
private fun PreviewTile(title: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val w = if (small) 64.dp else 78.dp
    val shape = RoundedCornerShape(w * 0.14f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(w + 10.dp)) {
        Box(
            Modifier
                .size(width = w, height = w * 1.9f)
                .clip(shape)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                .bouncyClick(onClick = onClick),
        ) {
            content()
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
        Text(title, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}
