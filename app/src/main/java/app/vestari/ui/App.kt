@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.vestari.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vestari.data.Repository
import app.vestari.ui.components.bouncyClick
import app.vestari.ui.screens.AddAccountScreen
import app.vestari.ui.screens.ComposeScreen
import app.vestari.ui.screens.FoldersScreen
import app.vestari.ui.screens.InboxScreen
import app.vestari.ui.screens.MessageScreen
import app.vestari.ui.screens.ProfileScreen
import app.vestari.ui.screens.SearchScreen
import app.vestari.ui.theme.AppTheme
import app.vestari.ui.theme.VestariTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ---------------- навигация ----------------

sealed interface Route {
    data object Main : Route
    data class Message(val key: String) : Route
    data class Compose(val replyToKey: String? = null, val to: String = "", val draftId: String? = null) : Route
    data object Drafts : Route
    data class ServerFolder(val accountId: String, val folder: String, val title: String) : Route
    data object AddAccount : Route
    data object Smart : Route
    data object Appearance : Route
}

enum class Tab(val title: String, val icon: ImageVector) {
    Mail("Почта", Icons.Outlined.Email),
    Folders("Папки", Icons.Outlined.Folder),
    Search("Поиск", Icons.Outlined.Search),
    Profile("Профиль", Icons.Outlined.Person),
}

class Nav {
    val stack = mutableStateListOf<Route>(Route.Main)
    var forward by mutableStateOf(true)
        private set
    var tab by mutableStateOf(Tab.Mail)
    /** Выбранная папка на экране «Почта»: null — все письма. */
    var folder by mutableStateOf<String?>(null)
    var accountSheetOpen by mutableStateOf(false)
    /** Панель папок ящика (открывается, если потянуть список вниз). */
    var folderPanelOpen by mutableStateOf(false)

    val current: Route get() = stack.last()
    fun push(r: Route) { forward = true; stack.add(r) }
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

val LocalNav = compositionLocalOf<Nav> { error("Nav не задан") }

// ---------------- всплывающие сообщения ----------------

class Toasts(val host: SnackbarHostState, private val scope: CoroutineScope) {
    var isError by mutableStateOf(false)
        private set

    /** Показывает сообщение-«пилюлю»; если есть [action] и его нажали — вызывает [onAction]. */
    fun show(text: String, action: String? = null, error: Boolean = false, onAction: () -> Unit = {}, onTimeout: () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            isError = error
            val r = host.showSnackbar(text, actionLabel = action, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onAction() else onTimeout()
        }
    }
}

val LocalToasts = compositionLocalOf<Toasts> { error("Toasts не заданы") }

// ---------------- общий переход «карточка → письмо» ----------------

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalRouteAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun Modifier.sharedCard(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalRouteAnimScope.current ?: return this
    return with(shared) {
        this@sharedCard.sharedBounds(
            rememberSharedContentState("card-$key"),
            animatedVisibilityScope = anim,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.85f, stiffness = 380f) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

// ---------------- корень приложения ----------------

@Composable
fun VestariApp(openMessageKey: String?, onOpenedFromNotification: () -> Unit) {
    val settings by Repository.settings.collectAsState()
    VestariTheme(settings.palette, settings.darkMode) {
        val nav = remember { Nav() }
        val scope = rememberCoroutineScope()
        val hostState = remember { SnackbarHostState() }
        val toasts = remember { Toasts(hostState, scope) }
        val error by Repository.lastError.collectAsState()

        LaunchedEffect(openMessageKey) {
            if (openMessageKey != null) {
                nav.tab = Tab.Mail
                nav.push(Route.Message(openMessageKey))
                onOpenedFromNotification()
            }
        }
        LaunchedEffect(Unit) { app.vestari.update.Updater.check(silent = true) }
        LaunchedEffect(error) {
            error?.let { toasts.show(it, error = true); Repository.clearError() }
        }
        BackHandler(enabled = nav.stack.size > 1) { nav.pop() }
        BackHandler(enabled = nav.stack.size == 1 && nav.tab != Tab.Mail) { nav.tab = Tab.Mail }

        CompositionLocalProvider(LocalNav provides nav, LocalToasts provides toasts, app.vestari.ui.components.LocalLook provides settings) {
            Box(Modifier.fillMaxSize()) {
                // Единый фон всех разделов: узор и своя картинка из «Внешнего вида».
                app.vestari.ui.components.AppBackdrop(settings, Modifier.fillMaxSize())
                SharedTransitionLayout {
                    AnimatedContent(
                        targetState = nav.current,
                        contentKey = { it.toString() },
                        transitionSpec = {
                            val dir = if (nav.forward) 1 else -1
                            val spec = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.9f, stiffness = 420f)
                            (slideInHorizontally(spec) { w -> dir * w / 3 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(spec) { w -> -dir * w / 4 } + fadeOut(tween(180)))
                        },
                        label = "routes",
                    ) { route ->
                        CompositionLocalProvider(
                            LocalSharedScope provides this@SharedTransitionLayout,
                            LocalRouteAnimScope provides this,
                        ) {
                            when (route) {
                                Route.Main -> MainScreen()
                                is Route.Message -> MessageScreen(route.key)
                                is Route.Compose -> ComposeScreen(route.replyToKey, route.to, route.draftId)
                                Route.Drafts -> app.vestari.ui.screens.DraftsScreen()
                                is Route.ServerFolder -> app.vestari.ui.screens.ServerFolderScreen(route.accountId, route.folder, route.title)
                                Route.AddAccount -> AddAccountScreen()
                                Route.Smart -> app.vestari.ui.screens.SmartScreen()
                                Route.Appearance -> app.vestari.ui.screens.AppearanceScreen()
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = if (nav.current == Route.Main) 92.dp else 16.dp),
                ) { data -> PillSnackbar(data.visuals.message, data.visuals.actionLabel, toasts.isError) { data.performAction() } }
            }
        }
    }
}

@Composable
private fun PillSnackbar(text: String, action: String?, error: Boolean, onAction: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (AppTheme.extra.isDark) Color(0xFF2E2A40) else AppTheme.extra.accentSoft,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (error) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle, null,
                tint = if (error) AppTheme.extra.unreadDot else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(text, modifier = Modifier.weight(1f).padding(vertical = 10.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (action != null) {
                TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val nav = LocalNav.current
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = nav.tab,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220))) togetherWith
                    (fadeOut(tween(150)) + scaleOut(targetScale = 1.01f, animationSpec = tween(150)))
            },
            label = "tabs",
        ) { tab ->
            when (tab) {
                Tab.Mail -> InboxScreen()
                Tab.Folders -> FoldersScreen()
                Tab.Search -> SearchScreen()
                Tab.Profile -> ProfileScreen()
            }
        }
        app.vestari.update.UpdateBanner(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
        BottomBar(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BottomBar(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val extra = AppTheme.extra
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = extra.card.copy(alpha = 0.97f),
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tab.entries.forEach { tab ->
                val selected = nav.tab == tab
                val bg by animateColorAsState(
                    if (selected) extra.accentSoft else Color.Transparent,
                    spring(stiffness = Spring.StiffnessMediumLow), label = "tabBg",
                )
                val fg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabFg",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .background(bg, RoundedCornerShape(20.dp))
                        .bouncyClick { nav.tab = tab }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, tab.title, tint = fg, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.title, color = fg, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
