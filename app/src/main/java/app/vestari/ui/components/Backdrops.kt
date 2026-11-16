package app.vestari.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import app.vestari.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Фоны экрана письма. Все готовые фоны рисуются кодом (без картинок в APK) и подстраиваются
 * под цвет оформления и тёмную тему. «custom» — своя картинка пользователя.
 */
object Backdrops {
    const val NONE = "none"
    const val WAVE = "wave"
    const val CUSTOM = "custom"

    /** Готовые фоны: id и название. */
    val presets = listOf(
        NONE to "Без фона",
        WAVE to "Волна",
        "aurora" to "Сияние",
        "hills" to "Холмы",
        "bubbles" to "Пузыри",
        "sunset" to "Закат",
    )
}

private val Blue = Color(0xFF6FC3FF)

/**
 * Фон на всю площадь: градиент темы и выбранный рисунок поверх.
 * [preview] — уменьшенная копия для плиток выбора в настройках.
 */
@Composable
fun Backdrop(id: String, customPath: String?, modifier: Modifier = Modifier) {
    val extra = AppTheme.extra
    val primary = MaterialTheme.colorScheme.primary
    val dark = extra.isDark
    Box(modifier.background(Brush.verticalGradient(listOf(extra.bgTop, extra.bgBottom)))) {
        if (id == Backdrops.CUSTOM && customPath != null) {
            val bmp = rememberBitmap(customPath)
            if (bmp != null) {
                Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                // Лёгкая вуаль, чтобы текст оставался читаемым на любой картинке.
                Box(Modifier.fillMaxSize().background(extra.bgTop.copy(alpha = if (dark) 0.62f else 0.55f)))
            }
            return@Box
        }
        val a = if (dark) 0.22f else 0.30f
        Canvas(Modifier.fillMaxSize()) {
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
}

/** Своя картинка, уменьшенная при загрузке (экран не грузит фото в полном размере). */
@Composable
private fun rememberBitmap(path: String): ImageBitmap? =
    produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(path)
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, opts)
                var sample = 1
                while (opts.outWidth / sample > 1440 || opts.outHeight / sample > 2560) sample *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }.value
