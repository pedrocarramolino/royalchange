package com.royalchance.feature.lobby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.component.SectionHeader
import com.royalchance.core.designsystem.component.feltBackground
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.theme.RoyalSizes
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.ui.AvatarBadge
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.game.GameType
import com.royalchance.feature.lobby.resources.Res
import com.royalchance.feature.lobby.resources.game_blackjack
import com.royalchance.feature.lobby.resources.game_blackjack_description
import com.royalchance.feature.lobby.resources.game_dice
import com.royalchance.feature.lobby.resources.game_dice_description
import com.royalchance.feature.lobby.resources.game_poker
import com.royalchance.feature.lobby.resources.game_poker_description
import com.royalchance.feature.lobby.resources.game_roulette
import com.royalchance.feature.lobby.resources.game_roulette_description
import com.royalchance.feature.lobby.resources.game_slots
import com.royalchance.feature.lobby.resources.game_slots_description
import com.royalchance.feature.lobby.resources.lobby_coming_soon
import com.royalchance.feature.lobby.resources.lobby_games
import com.royalchance.feature.lobby.resources.lobby_greeting
import com.royalchance.feature.lobby.resources.lobby_greeting_guest
import com.royalchance.feature.lobby.resources.lobby_guest_action
import com.royalchance.feature.lobby.resources.lobby_guest_banner
import com.royalchance.feature.lobby.resources.lobby_subtitle
import com.royalchance.feature.lobby.resources.lobby_verify_action
import com.royalchance.feature.lobby.resources.lobby_verify_banner
import com.royalchance.feature.lobby.resources.lobby_verify_sent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LobbyScreen(
    viewModel: LobbyViewModel,
    onCreateAccount: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // En escritorio el contenido se centra con un ancho máximo cómodo de leer.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(RoyalSpacing.l),
            horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.m),
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.m),
            modifier = Modifier.fillMaxSize().widthIn(max = RoyalSizes.contentMaxWidth),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LobbyHeader(alias = state.user?.profile?.alias, avatar = state.user?.profile?.avatar ?: AvatarId.SpadeGold)
            }
            if (state.showGuestBanner) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    InfoBanner(
                        message = stringResource(Res.string.lobby_guest_banner),
                        tone = BannerTone.Warning,
                        actionLabel = stringResource(Res.string.lobby_guest_action),
                        onAction = onCreateAccount,
                    )
                }
            }
            if (state.verificationJustConfirmed) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    InfoBanner(message = stringResource(Res.string.lobby_verify_sent), tone = BannerTone.Success, icon = RoyalIcons.Check)
                }
            } else if (state.showVerificationBanner) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    InfoBanner(
                        message = stringResource(Res.string.lobby_verify_banner),
                        icon = RoyalIcons.Mail,
                        actionLabel = stringResource(Res.string.lobby_verify_action).takeUnless { state.isSendingVerification },
                        onAction = viewModel::sendVerification,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(stringResource(Res.string.lobby_games))
            }
            items(state.games, key = { it.name }) { game -> GameCard(game) }
        }
    }
}

@Composable
private fun LobbyHeader(alias: String?, avatar: AvatarId) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = RoyalSpacing.s),
    ) {
        AvatarBadge(avatar = avatar, size = 56.dp)
        Spacer(Modifier.padding(horizontal = RoyalSpacing.s))
        Column {
            Text(
                text = alias?.let { stringResource(Res.string.lobby_greeting, it) } ?: stringResource(Res.string.lobby_greeting_guest),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.lobby_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val GameType.title: StringResource
    get() = when (this) {
        GameType.Blackjack -> Res.string.game_blackjack
        GameType.Roulette -> Res.string.game_roulette
        GameType.Slots -> Res.string.game_slots
        GameType.Poker -> Res.string.game_poker
        GameType.Dice -> Res.string.game_dice
    }

private val GameType.description: StringResource
    get() = when (this) {
        GameType.Blackjack -> Res.string.game_blackjack_description
        GameType.Roulette -> Res.string.game_roulette_description
        GameType.Slots -> Res.string.game_slots_description
        GameType.Poker -> Res.string.game_poker_description
        GameType.Dice -> Res.string.game_dice_description
    }

/** Tarjeta de juego. En esta fase los juegos aún no están disponibles: se muestran como "Próximamente". */
@Composable
private fun GameCard(game: GameType) {
    val casino = RoyalTheme.casinoColors
    val title = stringResource(game.title)
    val description = stringResource(game.description)
    val comingSoon = stringResource(Res.string.lobby_coming_soon)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$title. $description. $comingSoon" },
    ) {
        Column {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().height(132.dp).feltBackground(casino.feltBrush),
            ) {
                GameArt(game)
            }
            Column(Modifier.padding(RoyalSpacing.l), verticalArrangement = Arrangement.spacedBy(RoyalSpacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 3)
                Spacer(Modifier.height(RoyalSpacing.xs))
                Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        text = comingSoon.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = RoyalSpacing.s, vertical = RoyalSpacing.xxs),
                    )
                }
            }
        }
    }
}
