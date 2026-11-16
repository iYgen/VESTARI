package app.vestari.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Цветовые гаммы. Чтобы добавить новую — допишите Palette в список, она сама появится в Профиле.
 */
@Immutable
data class Palette(
    val name: String,
    val accent: Color,        // кнопки, выбранные элементы
    val accentSoft: Color,    // фон выбранного чипа / строки
    val bgTop: Color,         // верх градиента фона
    val bgBottom: Color,      // низ градиента фона
    val card: Color,
    val darkAccent: Color,
    val darkBgTop: Color,
    val darkBgBottom: Color,
    val darkCard: Color,
)

val Palettes = listOf(
    Palette("Лаванда", Color(0xFF6C5CE7), Color(0xFFE6E1FB), Color(0xFFF3F1FD), Color(0xFFE4E0FA), Color(0xFFFFFFFF),
        Color(0xFFA99CFF), Color(0xFF17151F), Color(0xFF221E33), Color(0xFF26233A)),
    // Фирменные цвета логотипа Vestari.
    Palette("Vestari", Color(0xFF2B44E6), Color(0xFFDDE3FD), Color(0xFFF1F3FE), Color(0xFFDCE6FB), Color(0xFFFFFFFF),
        Color(0xFF7F9BFF), Color(0xFF12141F), Color(0xFF181F38), Color(0xFF1D2540)),
    Palette("Океан", Color(0xFF1E7BE0), Color(0xFFDCEBFD), Color(0xFFF1F6FE), Color(0xFFDDE9FB), Color(0xFFFFFFFF),
        Color(0xFF7DB7FF), Color(0xFF111821), Color(0xFF172436), Color(0xFF1C2A3D)),
    Palette("Мята", Color(0xFF14A37F), Color(0xFFD7F3EA), Color(0xFFF0FAF6), Color(0xFFD9F1E8), Color(0xFFFFFFFF),
        Color(0xFF6FDDBE), Color(0xFF101A17), Color(0xFF15281F), Color(0xFF1B2D26)),
    Palette("Закат", Color(0xFFE5574F), Color(0xFFFCE1DD), Color(0xFFFFF4F1), Color(0xFFFBDDD6), Color(0xFFFFFFFF),
        Color(0xFFFF9A90), Color(0xFF1C1413), Color(0xFF2E1D1B), Color(0xFF332220)),
    Palette("Графит", Color(0xFF3A3F4B), Color(0xFFE3E5EA), Color(0xFFF5F6F8), Color(0xFFE3E5EA), Color(0xFFFFFFFF),
        Color(0xFFC2C8D6), Color(0xFF121314), Color(0xFF1C1E22), Color(0xFF23262B)),
)

/** Цвета аватарок ящиков. */
val AvatarColors = listOf(
    Color(0xFF8E7CF0), Color(0xFF4C9BF5), Color(0xFF33B58A), Color(0xFFF08A5D), Color(0xFFE05D8C), Color(0xFF5D6BE0),
)

/** Дополнительные цвета, которых нет в Material ColorScheme. */
@Immutable
data class ExtraColors(
    val bgTop: Color,
    val bgBottom: Color,
    val card: Color,
    val accentSoft: Color,
    val muted: Color,
    val unreadDot: Color = Color(0xFFF2564F),
    val star: Color = Color(0xFFFFB400),
    val isDark: Boolean,
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Color.White, Color.White, Color.White, Color.LightGray, Color.Gray, isDark = false)
}

object AppTheme {
    val extra: ExtraColors @Composable get() = LocalExtraColors.current
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(lineHeight = 22.sp),
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun VestariTheme(paletteIndex: Int, darkMode: Int, content: @Composable () -> Unit) {
    val p = Palettes.getOrElse(paletteIndex) { Palettes.first() }
    val dark = when (darkMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }

    // Плавная смена цветов при переключении гаммы или тёмной темы.
    val anim = tween<Color>(450)
    val accent by animateColorAsState(if (dark) p.darkAccent else p.accent, anim, label = "accent")
    val bgTop by animateColorAsState(if (dark) p.darkBgTop else p.bgTop, anim, label = "bgTop")
    val bgBottom by animateColorAsState(if (dark) p.darkBgBottom else p.bgBottom, anim, label = "bgBottom")
    val card by animateColorAsState(if (dark) p.darkCard else p.card, anim, label = "card")
    val soft by animateColorAsState(if (dark) p.darkAccent.copy(alpha = 0.18f) else p.accentSoft, anim, label = "soft")

    val scheme: ColorScheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = Color(0xFF14121C), primaryContainer = soft, onPrimaryContainer = Color.White,
        background = bgTop, surface = card, surfaceContainerLow = card, surfaceContainer = card,
        surfaceContainerHigh = card, onSurface = Color(0xFFF1EFF7), onSurfaceVariant = Color(0xFFA9A5B8),
        outlineVariant = Color(0x22FFFFFF),
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White, primaryContainer = soft, onPrimaryContainer = Color(0xFF1C1640),
        background = bgTop, surface = card, surfaceContainerLow = card, surfaceContainer = Color(0xFFF7F6FC),
        surfaceContainerHigh = Color.White, onSurface = Color(0xFF1B1A22), onSurfaceVariant = Color(0xFF6E6B7B),
        outlineVariant = Color(0x14000000),
    )
    val extra = ExtraColors(
        bgTop = bgTop, bgBottom = bgBottom, card = card, accentSoft = soft,
        muted = if (dark) Color(0xFF8E8A9E) else Color(0xFF8A8797), isDark = dark,
    )
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
