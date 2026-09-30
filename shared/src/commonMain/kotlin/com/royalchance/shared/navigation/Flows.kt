package com.royalchance.shared.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.royalchance.core.designsystem.component.AdaptiveNavigationScaffold
import com.royalchance.core.designsystem.component.BrandEmblem
import com.royalchance.core.designsystem.component.BrandWordmark
import com.royalchance.core.designsystem.component.NavigationItem
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.ui.AvatarBadge
import com.royalchance.domain.auth.AuthUser
import com.royalchance.domain.auth.AvatarId
import com.royalchance.feature.auth.legal.LegalDocument
import com.royalchance.feature.auth.navigation.AuthRoute
import com.royalchance.feature.auth.navigation.authEntries
import com.royalchance.feature.history.HistoryRoute
import com.royalchance.feature.history.historyEntry
import com.royalchance.feature.lobby.LobbyRoute
import com.royalchance.feature.lobby.lobbyEntry
import com.royalchance.feature.profile.ProgressRoute
import com.royalchance.feature.profile.progressEntry
import com.royalchance.feature.settings.SettingsRoute
import com.royalchance.feature.settings.settingsEntry
import com.royalchance.shared.AppGraph
import com.royalchance.shared.AppInfo
import com.royalchance.shared.resources.Res
import com.royalchance.shared.resources.drawer_guest
import com.royalchance.shared.resources.tab_casino
import com.royalchance.shared.resources.tab_history
import com.royalchance.shared.resources.tab_progress
import com.royalchance.shared.resources.tab_settings
import org.jetbrains.compose.resources.stringResource

/** Acceso sin sesión: bienvenida, inicio de sesión, registro… */
@Composable
internal fun AuthFlow(graph: AppGraph) {
    AuthOnlyFlow(graph, start = AuthRoute.Welcome)
}

/** Cuenta autenticada sin perfil (p. ej. tras entrar con Google por primera vez). */
@Composable
internal fun CompleteProfileFlow(graph: AppGraph) {
    AuthOnlyFlow(graph, start = AuthRoute.CompleteProfile)
}

@Composable
private fun AuthOnlyFlow(graph: AppGraph, start: AuthRoute) {
    val backStack = rememberNavBackStack(NavigationConfiguration, start)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.popIfNotRoot() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            authEntries(
                dependencies = graph.authDependencies,
                navigate = { backStack.add(it) },
                back = { backStack.popIfNotRoot() },
            )
        },
    )
}

private enum class MainTab(val route: NavKey) {
    Casino(LobbyRoute),
    Progress(ProgressRoute),
    History(HistoryRoute),
    Settings(SettingsRoute),
}

/**
 * Casino con sesión iniciada. La pila siempre empieza en el lobby; cambiar de pestaña deja
 * `[lobby, pestaña]`, de modo que "atrás" desde cualquier pestaña vuelve al lobby.
 * Las pantallas empujadas encima (p. ej. crear cuenta) ocultan la navegación principal.
 */
@Composable
internal fun MainFlow(graph: AppGraph, user: AuthUser?) {
    val backStack = rememberNavBackStack(NavigationConfiguration, LobbyRoute)
    val currentTab = backStack.lastOrNull { key -> MainTab.entries.any { it.route == key } }
        ?.let { key -> MainTab.entries.first { it.route == key } }
        ?: MainTab.Casino
    val onTabRoot = MainTab.entries.any { it.route == backStack.lastOrNull() }

    val items = listOf(
        NavigationItem(MainTab.Casino, stringResource(Res.string.tab_casino), RoyalIcons.Spade),
        NavigationItem(MainTab.Progress, stringResource(Res.string.tab_progress), RoyalIcons.Trophy),
        NavigationItem(MainTab.History, stringResource(Res.string.tab_history), RoyalIcons.History),
        NavigationItem(MainTab.Settings, stringResource(Res.string.tab_settings), RoyalIcons.Settings),
    )

    AdaptiveNavigationScaffold(
        items = items,
        selected = currentTab,
        onSelect = { tab -> backStack.selectTab(tab) },
        navigationVisible = onTabRoot,
        drawerHeader = { DrawerHeader(user) },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.popIfNotRoot() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                val openCreateAccount: () -> Unit = { backStack.add(AuthRoute.Register) }
                lobbyEntry(graph.authRepository, onCreateAccount = openCreateAccount)
                progressEntry()
                historyEntry()
                settingsEntry(
                    authRepository = graph.authRepository,
                    settingsRepository = graph.settingsRepository,
                    appVersion = AppInfo.VERSION,
                    onCreateAccount = openCreateAccount,
                    onOpenTerms = { backStack.add(AuthRoute.Legal(LegalDocument.Terms)) },
                    onOpenPrivacy = { backStack.add(AuthRoute.Legal(LegalDocument.Privacy)) },
                )
                // Un invitado puede crear su cuenta sin salir del casino; al terminar vuelve atrás.
                authEntries(
                    dependencies = graph.authDependencies,
                    navigate = { backStack.add(it) },
                    back = { backStack.popIfNotRoot() },
                    onRegistrationCompleted = { backStack.popIfNotRoot() },
                )
            },
        )
    }
}

@Composable
private fun DrawerHeader(user: AuthUser?) {
    Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.l), modifier = Modifier.padding(RoyalSpacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandEmblem(size = 44.dp)
            Spacer(Modifier.width(RoyalSpacing.m))
            BrandWordmark(
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MaterialTheme.typography.headlineSmall.fontFamily),
                letterSpacing = 2.sp,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBadge(avatar = user?.profile?.avatar ?: AvatarId.SpadeGold, size = 40.dp)
            Spacer(Modifier.width(RoyalSpacing.m))
            Text(
                text = user?.profile?.alias ?: stringResource(Res.string.drawer_guest),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun NavBackStack<NavKey>.selectTab(tab: MainTab) {
    while (size > 1) removeAt(lastIndex)
    if (tab != MainTab.Casino) add(tab.route)
}

/** La raíz nunca se retira: una pila vacía no tiene pantalla que mostrar. */
private fun NavBackStack<NavKey>.popIfNotRoot() {
    if (size > 1) removeAt(lastIndex)
}
