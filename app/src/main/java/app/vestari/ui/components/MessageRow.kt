package app.vestari.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.vestari.data.Category
import app.vestari.data.ListStyle
import app.vestari.data.MailMessage
import app.vestari.ui.theme.AppTheme
import app.vestari.ui.LocalToasts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp

/** Найденный код подтверждения: нажатие копирует его в буфер обмена. */
@Composable
fun OtpChip(code: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val toasts = LocalToasts.current
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .bouncyClick {
                clipboard.setText(AnnotatedString(code))
                toasts.show("Код $code скопирован")
            }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            code.chunked(if (code.length > 4) 3 else 4).joinToString(" "),
            color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.ContentCopy, "Скопировать", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
    }
}

/** Отступ между письмами в списке для каждого вида. */
fun ListStyle.spacing() = when (this) {
    ListStyle.Cards -> 12.dp
    ListStyle.Compact -> 8.dp
    ListStyle.List -> 2.dp
}

/**
 * Письмо в списке. Три вида (настраивается в профиле). Непрочитанные выделены везде одинаково:
 * цветная полоска слева, подкрашенный фон, точка и жирный шрифт.
 */
@Composable
fun MessageCard(
    m: MailMessage,
    category: Category?,
    modifier: Modifier = Modifier,
    showAccount: String? = null,
    style: ListStyle = ListStyle.Cards,
    showPreview: Boolean = true,
    otp: String? = null,
    /** Письмо выбрано (режим выделения): вместо иконки — галочка, фон подсвечен. */
    selected: Boolean = false,
    /** Нажатие на иконку письма — выделить его. null — иконка не нажимается. */
    onTileClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStar: () -> Unit,
) {
    val extra = AppTheme.extra
    val primary = MaterialTheme.colorScheme.primary
    val unread = !m.seen
    val base = if (style == ListStyle.List) Color.Transparent else extra.card
    val bg by animateColorAsState(
        when {
            selected -> lerp(extra.card, primary, if (extra.isDark) 0.28f else 0.18f)
            !unread -> base
            style == ListStyle.List -> extra.accentSoft.copy(alpha = 0.55f)
            else -> lerp(extra.card, primary, if (extra.isDark) 0.10f else 0.06f)
        }, label = "cardBg",
    )
    val stripe by animateColorAsState(if (unread) primary else Color.Transparent, label = "stripe")
    val radius = when (style) { ListStyle.Cards -> 24.dp; ListStyle.Compact -> 18.dp; ListStyle.List -> 14.dp }
    val pad = when (style) { ListStyle.Cards -> 16.dp; ListStyle.Compact -> 12.dp; ListStyle.List -> 10.dp }
    val tile = when (style) { ListStyle.Cards -> 52.dp; ListStyle.Compact -> 42.dp; ListStyle.List -> 36.dp }

    Surface(
        modifier = modifier.fillMaxWidth().bouncyClick(onLongClick = onLongClick, onClick = onClick),
        shape = RoundedCornerShape(radius),
        color = bg,
        shadowElevation = if (extra.isDark || style == ListStyle.List) 0.dp else 1.dp,
    ) {
        Row(
            Modifier
                .drawBehind {
                    if (stripe.alpha > 0f) drawRoundRect(
                        stripe, topLeft = Offset(0f, size.height * 0.18f),
                        size = Size(4.dp.toPx(), size.height * 0.64f), cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
                .padding(start = pad + 2.dp, end = pad - 4.dp, top = pad, bottom = pad),
            verticalAlignment = if (style == ListStyle.Cards) Alignment.Top else Alignment.CenterVertically,
        ) {
            Box(
                if (onTileClick != null) Modifier.clip(RoundedCornerShape(16.dp)).bouncyClick(onClick = onTileClick) else Modifier,
            ) {
                CategoryTile(category?.id, category?.let { iconFor(it.icon) }, m.senderTitle, tile)
                androidx.compose.animation.AnimatedVisibility(selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Box(
                        Modifier.size(tile).clip(RoundedCornerShape(16.dp)).background(primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Check, "Выбрано", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(tile * 0.5f))
                    }
                }
            }
            Spacer(Modifier.width(if (style == ListStyle.List) 10.dp else 14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(unread, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        Box(Modifier.padding(end = 6.dp).size(8.dp).clip(CircleShape).background(extra.unreadDot))
                    }
                    Text(
                        m.senderTitle,
                        style = if (style == ListStyle.Cards) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatDate(m.date), style = MaterialTheme.typography.bodySmall,
                        color = if (unread) primary else extra.muted,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(if (style == ListStyle.Cards) 3.dp else 1.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val subjectWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal
                        val subjectColor = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        if (style == ListStyle.Cards) {
                            Text(m.subject, style = MaterialTheme.typography.bodyLarge, fontWeight = subjectWeight,
                                color = subjectColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (showPreview && m.preview.isNotBlank()) {
                                Text(m.preview, style = MaterialTheme.typography.bodyMedium, color = extra.muted,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            // Тема и превью в одну строку: «Тема — начало текста…»
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = subjectWeight, color = subjectColor)) { append(m.subject) }
                                    if (showPreview && m.preview.isNotBlank()) {
                                        withStyle(SpanStyle(color = extra.muted)) { append(" — "); append(m.preview) }
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (style == ListStyle.Compact && showPreview) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (otp != null) OtpChip(otp, Modifier.padding(top = 6.dp))
                        if (showAccount != null) {
                            Text(showAccount, style = MaterialTheme.typography.labelSmall, color = primary,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    StarButton(m.flagged, onStar)
                }
            }
        }
    }
}
