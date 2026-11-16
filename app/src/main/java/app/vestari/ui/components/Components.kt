@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.vestari.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vestari.data.Account
import app.vestari.data.Category
import app.vestari.data.MailMessage
import app.vestari.ui.theme.AppTheme
import app.vestari.ui.theme.AvatarColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Иконки, которые можно выбрать для своей папки. */
val FolderIcons: List<Pair<String, ImageVector>> = listOf(
    "folder" to Icons.Outlined.Folder,
    "receipt" to Icons.Outlined.Receipt,
    "tag" to Icons.Outlined.LocalOffer,
    "plane" to Icons.Outlined.Flight,
    "megaphone" to Icons.Outlined.Campaign,
    "bank" to Icons.Outlined.AccountBalance,
    "mail" to Icons.Outlined.Email,
    "bag" to Icons.Outlined.ShoppingBag,
    "work" to Icons.Outlined.Work,
    "home" to Icons.Outlined.Home,
    "heart" to Icons.Outlined.FavoriteBorder,
    "star" to Icons.Outlined.StarOutline,
    "school" to Icons.Outlined.School,
    "game" to Icons.Outlined.SportsEsports,
    "pet" to Icons.Outlined.Pets,
)

fun iconFor(key: String): ImageVector = FolderIcons.firstOrNull { it.first == key }?.second ?: Icons.Outlined.Folder

/** Мягкие цвета плиток для папок — по аналогии с макетом. */
fun tileColors(categoryId: String?, dark: Boolean): Pair<Color, Color> {
    val base = when (categoryId) {
        "receipts" -> Color(0xFF7B61FF)
        "sales" -> Color(0xFFF08A3C)
        "tickets" -> Color(0xFF3D86F5)
        "newsletters" -> Color(0xFFE0569A)
        "gov" -> Color(0xFF2FA572)
        "self" -> Color(0xFF8E7CF0)
        null -> Color(0xFF9A96AA)
        else -> AvatarColors[(categoryId.hashCode() and 0x7fffffff) % AvatarColors.size]
    }
    return base.copy(alpha = if (dark) 0.22f else 0.13f) to base
}

/** Нажатие с «пружинкой», как на iOS: элемент слегка сжимается и отскакивает. */
@Composable
fun Modifier.bouncyClick(
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick)
}

@Composable
fun AccountAvatar(account: Account?, size: Dp = 44.dp) {
    val color = account?.let { AvatarColors[it.color % AvatarColors.size] } ?: MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.75f), color))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (account?.name?.firstOrNull() ?: account?.email?.firstOrNull() ?: '✉').uppercaseChar().toString(),
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp,
        )
    }
}

@Composable
fun CategoryTile(categoryId: String?, icon: ImageVector?, letter: String, size: Dp = 52.dp) {
    val (bg, fg) = tileColors(categoryId, AppTheme.extra.isDark)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.5f))
        else Text(letter.take(1).uppercase(), color = fg, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

fun formatDate(ts: Long): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    val ru = Locale("ru")
    return when {
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("HH:mm", ru).format(c.time)
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR) == 1 -> "Вчера"
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> SimpleDateFormat("d MMM", ru).format(c.time).trimEnd('.')
        else -> SimpleDateFormat("dd.MM.yy", ru).format(c.time)
    }
}


@Composable
fun StarButton(active: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        if (active) 1.15f else 1f,
        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow), label = "star",
    )
    val tint by animateColorAsState(if (active) AppTheme.extra.star else AppTheme.extra.muted, label = "starTint")
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (active) Icons.Rounded.Star else Icons.Rounded.StarOutline, "Отметить",
            tint = tint, modifier = Modifier.size(22.dp).scale(scale),
        )
    }
}

/**
 * Чип папки с иконкой. Есть непрочитанные — название жирное и счётчик в скобках: «Чеки (3)»;
 * всё прочитано — обычный шрифт без счётчика.
 */
@Composable
fun FolderChip(
    text: String,
    selected: Boolean,
    badge: Int = 0,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else AppTheme.extra.card.copy(alpha = if (AppTheme.extra.isDark) 1f else 0.85f),
        spring(stiffness = Spring.StiffnessMediumLow), label = "chipBg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "chipFg",
    )
    Row(
        Modifier
            .padding(top = 6.dp, end = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .bouncyClick(onLongClick = onLongClick, onClick = onClick)
            .padding(start = if (icon != null) 12.dp else 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) fg else iconTint ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (badge > 0) "$text (${if (badge > 999) "999+" else badge})" else text,
            color = fg, style = MaterialTheme.typography.labelLarge,
            fontWeight = if (badge > 0) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(vertical = 8.dp))
}

@Composable
fun SoftRow(
    icon: ImageVector?, title: String, modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = tint, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
