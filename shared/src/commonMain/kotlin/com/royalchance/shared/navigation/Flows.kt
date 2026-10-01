package com.royalchance.shared.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.royalchance.core.ui.ChipBalance
import com.royalchance.core.ui.ProgressEventGate
import com.royalchance.core.ui.ProgressEventHost
import com.royalchance.domain.auth.AuthUser
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.game.GameType
import com.royalchance.feature.auth.legal.LegalDocument
import com.royalchance.feature.auth.navigation.AuthRoute
import com.royalchance.feature.auth.navigation.authEntries
import com.royalchance.feature.auth.navigation.legalEntries
import com.royalchance.feature.blackjack.BlackjackRoute
import com.royalchance.feature.blackjack.blackjackEntry
import com.royalchance.feature.dice.DiceRoute
import com.royalchance.feature.dice.diceEntry
import com.royalchance.feature.roulette.RouletteRoute
import com.royalchance.feature.roulette.rouletteEntry
import com.royalchance.feature.slots.SlotsRoute
import com.royalchance.feature.slots.slotsEntry
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

/** Cuenta creada sin perfil: el registro se interrumpió antes de guardarlo (p. ej. alias ya ocupado). */
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
 * Las pantallas empujadas encima (mesas de juego, documentos legales) ocultan la navegación principal.
 */
@Composable
internal fun MainFlow(graph: AppGraph, user: AuthUser?) {
    val backStack = rememberNavBackStack(NavigationConfiguration, LobbyRoute)
    val wallet by graph.economyRepository.wallet.collectAsStateWithLifecycle()
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

    val eventGate = remember { ProgressEventGate() }
    Box(Modifier.fillMaxSize()) {
        AdaptiveNavigationScaffold(
            items = items,
            selected = currentTab,
            onSelect = { tab -> backStack.selectTab(tab) },
            navigationVisible = onTabRoot,
            drawerHeader = { DrawerHeader(user, balance = (wallet as? WalletState.Ready)?.wallet?.balance) },
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.popIfNotRoot() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    lobbyEntry(
                        authRepository = graph.authRepository,
                        economyRepository = graph.economyRepository,
                        clock = graph.clock,
                        timeZone = graph.timeZone,
                        onOpenProgress = { backStack.selectTab(MainTab.Progress) },
                        onOpenGame = { game ->
                            when (game) {
                                GameType.Blackjack -> backStack.add(BlackjackRoute)
                                GameType.Roulette -> backStack.add(RouletteRoute)
                                GameType.Slots -> backStack.add(SlotsRoute)
                                GameType.Dice -> backStack.add(DiceRoute)
                                else -> Unit
                            }
                        },
                    )
                    blackjackEntry(graph.blackjackDependencies, eventGate, onBack = { backStack.popIfNotRoot() })
                    rouletteEntry(graph.rouletteDependencies, eventGate, onBack = { backStack.popIfNotRoot() })
                    slotsEntry(graph.slotsDependencies, eventGate, onBack = { backStack.popIfNotRoot() })
                    diceEntry(graph.diceDependencies, eventGate, onBack = { backStack.popIfNotRoot() })
                    progressEntry(graph.economyRepository, graph.clock, graph.timeZone)
                    historyEntry()
                    settingsEntry(
                        authRepository = graph.authRepository,
                        settingsRepository = graph.settingsRepository,
                        appVersion = AppInfo.VERSION,
                        onOpenTerms = { backStack.add(AuthRoute.Legal(LegalDocument.Terms)) },
                        onOpenPrivacy = { backStack.add(AuthRoute.Legal(LegalDocument.Privacy)) },
                    )
                    legalEntries(back = { backStack.popIfNotRoot() })
                },
            )
        }
        // Subidas de nivel y logros, sobre cualquier pantalla del casino.
        ProgressEventHost(
            events = graph.economyRepository.events,
            gate = eventGate,
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

@Composable
private fun DrawerHeader(user: AuthUser?, balance: Chips?) {
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
            Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.xxs)) {
                Text(
                    text = user?.profile?.alias ?: user?.email.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (balance != null) ChipBalance(balance, style = MaterialTheme.typography.labelLarge, glyphSize = 16.dp)
            }
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
