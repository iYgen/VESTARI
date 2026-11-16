package app.vestari.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vestari.data.PictureFit
import app.vestari.data.PicturePlace
import app.vestari.data.Settings
import app.vestari.ui.screens.AppLogo
import app.vestari.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Узоры фона. Все рисуются кодом (без картинок в APK) и подстраиваются
 * под цвет оформления и тёмную тему. Своя картинка задаётся отдельно (см. [AppBackdrop]).
 */
object Backdrops {
    const val NONE = "none"
    const val WAVE = "wave"

    /** Узоры: id и название. */
    val presets = listOf(
        NONE to "Без узора",
        WAVE to "Волна",
        "aurora" to "Сияние",
        "hills" to "Холмы",
        "bubbles" to "Пузыри",
        "sunset" to "Закат",
    )
}

/** Текущие настройки внешнего вида — чтобы любая часть интерфейса могла их прочитать без подписки. */
val LocalLook = compositionLocalOf { Settings() }

/** Скругление карточек по настройке «Углы». */
fun cornerScale(style: Int): Float = when (style) { 0 -> 0.35f; 2 -> 1.45f; else -> 1f }

private val Blue = Color(0xFF6FC3FF)

/**
 * Фон всего приложения: градиент темы, узор и (если задана) своя картинка — в шапке, внизу или на весь экран.
 * [scale] уменьшает высоту шапки и низа для мини-превью в настройках.
 */
@Composable
fun AppBackdrop(look: Settings, modifier: Modifier = Modifier, scale: Float = 1f) {
    val extra = AppTheme.extra
    Box(modifier.background(Brush.verticalGradient(listOf(extra.bgTop, extra.bgBottom)))) {
        val place = if (look.customBackdrop == null) PicturePlace.Off else look.picturePlace
        // Картинка на весь экран закрывает узор — рисовать его нет смысла.
        if (place != PicturePlace.Full) Pattern(look.composeBackdrop, Modifier.fillMaxSize())
        if (place == PicturePlace.Off) return@Box
        val bmp = rememberBitmap(look.customBackdrop!!) ?: return@Box
        val contentScale = when (look.pictureFit) {
            PictureFit.Crop -> ContentScale.Crop
            PictureFit.Width -> ContentScale.FillWidth
            PictureFit.Fit -> ContentScale.Fit
            PictureFit.Stretch -> ContentScale.FillBounds
        }
        val align = when (look.pictureAlign) { 0 -> Alignment.TopCenter; 2 -> Alignment.BottomCenter; else -> Alignment.Center }
        val veil = look.pictureVeil.coerceIn(0f, 0.9f)
        val h = (look.pictureHeight * scale).dp

        if (place == PicturePlace.Full) {
            Image(bmp, null, contentScale = contentScale, alignment = align, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(extra.bgTop.copy(alpha = veil)))
            return@Box
        }
        if (place == PicturePlace.Header || place == PicturePlace.Both) {
            Box(Modifier.fillMaxWidth().height(h).align(Alignment.TopCenter)) {
                Image(bmp, null, contentScale = contentScale, alignment = align, modifier = Modifier.fillMaxSize())
                // Картинка плавно растворяется в фоне к низу шапки.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(extra.bgTop.copy(alpha = veil * 0.7f), extra.bgTop.copy(alpha = veil), extra.bgTop))))
            }
        }
        if (place == PicturePlace.Footer || place == PicturePlace.Both) {
            Box(Modifier.fillMaxWidth().height(h).align(Alignment.BottomCenter)) {
                Image(bmp, null, contentScale = contentScale, alignment = align, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(extra.bgBottom, extra.bgBottom.copy(alpha = veil), extra.bgBottom.copy(alpha = veil * 0.7f)))))
            }
        }
    }
}

/** Узор фона по id из [Backdrops.presets]. */
@Composable
fun Pattern(id: String, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val a = if (AppTheme.extra.isDark) 0.22f else 0.30f
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (id) {
            Backdrops.WAVE -> {
                val top = minOf(h, w * 0.62f)
                drawPath(
                    Path().apply {
                        moveTo(w * 0.30f, 0f)
                        cubicTo(w * 0.55f, top * 0.25f, w * 0.62f, top * 0.70f, w, top * 0.78f)
                        lineTo(w, 0f); close()
                    },
                    Brush.linearGradient(listOf(Blue.copy(alpha = a * 0.6f), primary.copy(alpha = a * 0.5f)), Offset(w * 0.3f, 0f), Offset(w, top)),
                )
                drawPath(
                    Path().apply {
                        moveTo(w * 0.52f, 0f)
                        cubicTo(w * 0.70f, top * 0.20f, w * 0.78f, top * 0.48f, w, top * 0.52f)
                        lineTo(w, 0f); close()
                    },
                    Brush.linearGradient(listOf(Blue.copy(alpha = a), primary.copy(alpha = a * 0.7f)), Offset(w * 0.5f, 0f), Offset(w, top * 0.6f)),
                )
            }
            "aurora" -> {
                drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = a), Color.Transparent), Offset(w * 0.15f, h * 0.12f), w * 0.7f), w * 0.7f, Offset(w * 0.15f, h * 0.12f))
                drawCircle(Brush.radialGradient(listOf(Blue.copy(alpha = a), Color.Transparent), Offset(w * 0.95f, h * 0.30f), w * 0.6f), w * 0.6f, Offset(w * 0.95f, h * 0.30f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFB36BFF).copy(alpha = a * 0.8f), Color.Transparent), Offset(w * 0.5f, h * 0.95f), w * 0.8f), w * 0.8f, Offset(w * 0.5f, h * 0.95f))
            }
            "hills" -> {
                fun hill(y: Float, amp: Float, color: Color) = drawPath(
                    Path().apply {
                        moveTo(0f, h * y)
                        cubicTo(w * 0.3f, h * (y - amp), w * 0.6f, h * (y + amp), w, h * (y - amp * 0.5f))
                        lineTo(w, h); lineTo(0f, h); close()
                    },
                    color,
                )
                hill(0.70f, 0.08f, Blue.copy(alpha = a * 0.5f))
                hill(0.80f, 0.06f, primary.copy(alpha = a * 0.5f))
                hill(0.90f, 0.05f, primary.copy(alpha = a * 0.8f))
            }
            "bubbles" -> {
                listOf(
                    Triple(0.85f, 0.08f, 0.22f), Triple(0.10f, 0.30f, 0.12f), Triple(0.70f, 0.45f, 0.08f),
                    Triple(0.25f, 0.75f, 0.18f), Triple(0.90f, 0.85f, 0.14f), Triple(0.55f, 0.18f, 0.06f),
                ).forEachIndexed { i, (x, y, r) ->
                    val c = if (i % 2 == 0) primary else Blue
                    drawCircle(c.copy(alpha = a * 0.7f), w * r, Offset(w * x, h * y))
                }
            }
            "sunset" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFFF9A8B).copy(alpha = a * 0.9f), Color(0xFFFFC371).copy(alpha = a * 0.5f), Color.Transparent), 0f, h * 0.55f))
                drawCircle(Color(0xFFFFB25B).copy(alpha = a), w * 0.22f, Offset(w * 0.78f, h * 0.18f))
            }
        }
    }
}

/**
 * Заголовок раздела с логотипом — один и тот же во всех разделах.
 * Логотип и надпись «Vestari» включаются в «Внешнем виде».
 */
@Composable
fun BrandTitle(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    look: Settings = LocalLook.current,
    /** Нажатие на логотип (например, выбор ящика на «Почте»). */
    onLogoClick: (() -> Unit)? = null,
    /** Минимальный размер значка — в мини-превью он меньше обычного. */
    minLogo: Float = 20f,
) {
    val size = style.fontSize.value
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (look.showLogo) {
            val logo = (size * 1.25f).coerceIn(minLogo, 40f).dp
            Box(if (onLogoClick != null) Modifier.clip(RoundedCornerShape(logo * 0.3f)).bouncyClick(onClick = onLogoClick) else Modifier) {
                AppLogo(logo)
            }
            Spacer(Modifier.width((size * 0.4f).coerceIn(4f, 10f).dp))
        }
        Column {
            if (look.showLogo && look.logoText) {
                // Подпись масштабируется вместе с заголовком и никогда не переносится.
                val small = (size * 0.42f).coerceIn(3f, 10f)
                Text(
                    "VESTARI", fontSize = small.sp, letterSpacing = (small * 0.15f).sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, lineHeight = (small * 1.1f).sp,
                    maxLines = 1, softWrap = false,
                )
            }
            Text(text, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Мини-экран «как это будет выглядеть»: фон, картинка, шапка с логотипом и несколько писем.
 * Используется для превью в «Внешнем виде».
 */
@Composable
fun LookPreview(look: Settings, modifier: Modifier = Modifier, width: Dp = 96.dp) {
    val height = width * 1.9f
    val k = width.value / 400f
    val extra = AppTheme.extra
    val corner = (14 * cornerScale(look.cornerStyle) * k * 2.2f).dp
    Box(modifier.size(width, height).clip(RoundedCornerShape(width * 0.14f))) {
        AppBackdrop(look, Modifier.fillMaxSize(), scale = k)
        Column(Modifier.fillMaxSize().padding(horizontal = width * 0.07f, vertical = width * 0.08f)) {
            // Размер шрифта зависит от ширины превью: в маленьких плитках всё уменьшается пропорционально.
            BrandTitle(
                "Входящие",
                MaterialTheme.typography.titleSmall.copy(fontSize = (width.value / 11f).sp, lineHeight = (width.value / 9f).sp, fontWeight = FontWeight.Bold),
                look = look,
                minLogo = width.value * 0.12f,
            )
            Spacer(Modifier.height(width * 0.08f))
            Column(verticalArrangement = Arrangement.spacedBy(width * 0.04f)) {
                repeat(5) { i ->
                    Row(
                        Modifier.fillMaxWidth().height(width * 0.17f).clip(RoundedCornerShape(corner))
                            .background(extra.card.copy(alpha = 0.92f)).padding(width * 0.03f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(width * 0.11f).clip(RoundedCornerShape(corner * 0.7f)).background(MaterialTheme.colorScheme.primary.copy(alpha = if (i < 2) 0.8f else 0.3f)))
                        Spacer(Modifier.width(width * 0.04f))
                        Column(verticalArrangement = Arrangement.spacedBy(width * 0.025f)) {
                            Box(Modifier.width(width * (0.45f - i * 0.03f)).height(width * 0.03f).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (i < 2) 0.6f else 0.3f)))
                            Box(Modifier.width(width * 0.55f).height(width * 0.025f).clip(RoundedCornerShape(50)).background(extra.muted.copy(alpha = 0.35f)))
                        }
                    }
                }
            }
        }
    }
}

/** Своя картинка, уменьшенная при загрузке (экран не грузит фото в полном размере). */
@Composable
private fun rememberBitmap(path: String): ImageBitmap? =
    produceState<ImageBitmap?>(cache[path], path) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(path)
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, opts)
                var sample = 1
                while (opts.outWidth / sample > 1440 || opts.outHeight / sample > 2560) sample *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }?.also { cache.clear(); cache[path] = it }
    }.value

/** Последняя загруженная картинка: фон не мигает при переходах между экранами. */
private val cache = HashMap<String, ImageBitmap>()
