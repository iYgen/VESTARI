package app.vestari.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.vestari.data.MailBody
import app.vestari.data.MailClient
import app.vestari.data.Repository
import app.vestari.data.Smart
import app.vestari.ui.components.OtpChip
import app.vestari.ui.LocalNav
import app.vestari.ui.LocalToasts
import app.vestari.ui.Route
import app.vestari.ui.components.CategoryTile
import app.vestari.ui.components.MoveToFolderSheet
import app.vestari.ui.components.StarButton
import app.vestari.ui.components.iconFor
import app.vestari.ui.sharedCard
import app.vestari.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MessageScreen(key: String) {
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val messagesMap by Repository.messages.collectAsState()
    val assignments by Repository.assignments.collectAsState()
    val categories by Repository.categories.collectAsState()
    val remoteMap by Repository.remote.collectAsState()
    val m = remember(messagesMap, remoteMap, key) {
        messagesMap.values.asSequence().flatten().firstOrNull { it.key == key }
            ?: remoteMap.values.asSequence().flatten().firstOrNull { it.key == key }
    }

    var body by remember { mutableStateOf<MailBody?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showImages by remember { mutableStateOf(Repository.settings.value.autoImages) }
    var moving by remember { mutableStateOf(false) }

    LaunchedEffect(key) {
        val msg = Repository.find(key) ?: return@LaunchedEffect
        runCatching { Repository.loadBody(msg) }
            .onSuccess { body = it }
            .onFailure { loadError = it.message ?: "Не удалось загрузить письмо" }
    }

    Surface(
        modifier = Modifier.fillMaxSize().sharedCard(key),
        color = AppTheme.extra.bgTop,
    ) {
        if (m == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Письмо не найдено — возможно, оно удалено")
                    TextButton(onClick = { nav.pop() }) { Text("Назад") }
                }
            }
            return@Surface
        }
        val category = categoryOf(m, assignments)?.let { id -> categories.firstOrNull { it.id == id } }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Верхняя панель
                Row(
                    Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                    Spacer(Modifier.weight(1f))
                    StarButton(m.flagged) { Repository.setFlagged(m, !m.flagged) }
                    IconButton(onClick = { moving = true }) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, "В папку") }
                    IconButton(onClick = {
                        Repository.setSeen(m, false); nav.pop(); toasts.show("Отмечено непрочитанным")
                    }) { Icon(Icons.Outlined.MarkEmailUnread, "Непрочитанное") }
                    IconButton(onClick = {
                        nav.pop()
                        Repository.hideLocally(m)
                        toasts.show("Письмо удалено", "Отменить",
                            onAction = { Repository.unhide(m) }, onTimeout = { Repository.commitDelete(m) })
                    }) { Icon(Icons.Outlined.Delete, "Удалить") }
                }

                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(m.subject, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryTile(category?.id, category?.let { iconFor(it.icon) }, m.senderTitle, 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.senderTitle, style = MaterialTheme.typography.titleMedium)
                            Text(m.fromEmail, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                        }
                        Text(
                            SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(m.date),
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted,
                        )
                    }
                    if (category != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            category.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(AppTheme.extra.accentSoft, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    val code = remember(body) {
                        val b = body
                        if (!Repository.settings.value.otpCodes || b == null) null
                        else Smart.otp(m.subject, b.text ?: b.html?.let(MailClient::htmlToText).orEmpty())
                    }
                    if (code != null) {
                        Spacer(Modifier.height(12.dp))
                        Text("Код подтверждения — нажмите, чтобы скопировать",
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                        OtpChip(code, Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }

                AnimatedContent(
                    targetState = Triple(body, loadError, showImages),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "body",
                ) { (b, err, images) ->
                    when {
                        err != null -> Text(err, color = AppTheme.extra.unreadDot, modifier = Modifier.padding(20.dp))
                        b == null -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        else -> Column {
                            if (b.html != null && !images) {
                                TextButton(onClick = { showImages = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                                    Icon(Icons.Outlined.Image, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Показать картинки")
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().heightIn(min = 120.dp),
                            ) { MailWebView(b, images) }
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(110.dp))
            }

            ExtendedFloatingActionButton(
                onClick = { nav.push(Route.Compose(replyToKey = m.key)) },
                icon = { Icon(Icons.AutoMirrored.Outlined.Reply, null) },
                text = { Text("Ответить") },
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            )
        }
        if (moving) MoveToFolderSheet(m) { moving = false }
    }
}

/**
 * Письмо показывается в WebView без JavaScript. Картинки из интернета по умолчанию не грузятся:
 * через них рассылки отслеживают, открыли ли вы письмо.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MailWebView(body: MailBody, showImages: Boolean) {
    val html = body.html ?: ("<pre style='white-space:pre-wrap;font-family:sans-serif;font-size:16px'>" +
        (body.text ?: "").replace("&", "&amp;").replace("<", "&lt;") + "</pre>")
    val page = """
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>body{margin:16px;font-family:sans-serif;color:#1b1a22;word-wrap:break-word;overflow-wrap:anywhere}
        img{max-width:100%!important;height:auto!important} table{max-width:100%!important}</style></head>
        <body>$html</body></html>
    """.trimIndent()
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                isVerticalScrollBarEnabled = false
                setBackgroundColor(android.graphics.Color.WHITE)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString()))) }
                        return true
                    }
                }
            }
        },
        update = { wv ->
            // Картинки, вложенные в само письмо (data:), видны всегда; из интернета — по настройке или кнопке.
            wv.settings.loadsImagesAutomatically = true
            wv.settings.blockNetworkImage = !showImages
            wv.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Перезагружаем страницу и при смене режима картинок, иначе кнопка «Показать картинки» ничего не меняла.
            val tag = "$showImages|${page.hashCode()}"
            if (wv.tag != tag) {
                wv.tag = tag
                wv.loadDataWithBaseURL("https://vestari.local/", page, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Текст цитаты для ответа. */
fun quoteFor(text: String?, html: String?): String {
    val plain = text ?: html?.let(MailClient::htmlToText) ?: ""
    return plain.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.take(40).joinToString("\n") { "> $it" }
}
