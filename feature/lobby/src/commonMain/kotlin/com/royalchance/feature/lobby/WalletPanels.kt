package com.royalchance.feature.lobby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.ChipAmount
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.ui.ChipBalance
import com.royalchance.core.ui.chipsText
import com.royalchance.domain.economy.EconomyRules
import com.royalchance.domain.economy.RescueStatus
import com.royalchance.feature.lobby.resources.Res
import com.royalchance.feature.lobby.resources.lobby_balance_label
import com.royalchance.feature.lobby.resources.lobby_balance_loading
import com.royalchance.feature.lobby.resources.lobby_balance_unavailable
import com.royalchance.feature.lobby.resources.lobby_rescue_available
import com.royalchance.feature.lobby.resources.lobby_rescue_claim
import com.royalchance.feature.lobby.resources.lobby_rescue_cooldown
import com.royalchance.feature.lobby.resources.lobby_rescue_failed
import com.royalchance.feature.lobby.resources.lobby_time_hours
import com.royalchance.feature.lobby.resources.lobby_time_hours_minutes
import com.royalchance.feature.lobby.resources.lobby_time_minutes
import com.royalchance.feature.lobby.resources.lobby_welcome_dismiss
import com.royalchance.feature.lobby.resources.lobby_welcome_grant
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** Saldo destacado sobre tapete, en la cabecera del lobby. */
@Composable
internal fun BalanceCard(balance: BalanceUi, modifier: Modifier = Modifier) {
    val casino = RoyalTheme.casinoColors
    Surface(shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.xs),
            modifier = Modifier
                // Tapete iluminado desde detrás de la cifra: la tarjeta es mucho más ancha que alta.
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(casino.felt, casino.feltShadow),
                            center = Offset(size.width * 0.15f, size.height / 2f),
                            radius = maxOf(size.width, size.height) * 0.9f,
                        ),
                    )
                }
                .padding(horizontal = RoyalSpacing.xl, vertical = RoyalSpacing.l)
                // El saldo cambia tras cada ronda: los lectores de pantalla anuncian el nuevo valor.
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = stringResource(Res.string.lobby_balance_label).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = casino.onFelt.copy(alpha = 0.8f),
            )
            val amountStyle = MaterialTheme.typography.displaySmall
            when (balance) {
                is BalanceUi.Ready -> ChipBalance(balance.chips, style = amountStyle, color = casino.gold, glyphSize = 36.dp)
                BalanceUi.Loading -> ChipAmount(
                    amount = "…",
                    description = stringResource(Res.string.lobby_balance_loading),
                    style = amountStyle,
                    color = casino.gold,
                    glyphSize = 36.dp,
                )
                BalanceUi.Unavailable -> Text(
                    text = stringResource(Res.string.lobby_balance_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = casino.onFelt,
                )
            }
        }
    }
}

@Composable
internal fun WelcomeGrantBanner(onDismiss: () -> Unit) {
    InfoBanner(
        message = stringResource(Res.string.lobby_welcome_grant, chipsText(EconomyRules.WELCOME_GRANT)),
        tone = BannerTone.Success,
        icon = RoyalIcons.Spade,
        actionLabel = stringResource(Res.string.lobby_welcome_dismiss),
        onAction = onDismiss,
    )
}

/** Recarga gratuita: disponible para recoger o en cuenta atrás. Nada si no hace falta. */
@Composable
internal fun RescueBanner(
    rescue: RescueStatus,
    now: Instant,
    claiming: Boolean,
    failed: Boolean,
    onClaim: () -> Unit,
) {
    val grant = chipsText(EconomyRules.RESCUE_GRANT)
    Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s)) {
        when (rescue) {
            RescueStatus.NotNeeded -> Unit
            RescueStatus.Available -> InfoBanner(
                message = stringResource(Res.string.lobby_rescue_available, grant),
                tone = BannerTone.Warning,
                icon = RoyalIcons.Spade,
                actionLabel = stringResource(Res.string.lobby_rescue_claim).takeUnless { claiming },
                onAction = onClaim,
            )
            is RescueStatus.CoolingDown -> InfoBanner(
                message = stringResource(Res.string.lobby_rescue_cooldown, grant, remainingText(minutesUntil(now, rescue.availableAt))),
                icon = RoyalIcons.History,
            )
        }
        if (failed) InfoBanner(message = stringResource(Res.string.lobby_rescue_failed), tone = BannerTone.Error)
    }
}

/** Minutos que faltan, redondeados hacia arriba: nunca dice "0 min" si aún no está lista. */
internal fun minutesUntil(now: Instant, target: Instant): Long {
    val seconds = (target - now).inWholeSeconds
    return if (seconds <= 0) 0 else (seconds + 59) / 60
}

@Composable
private fun remainingText(minutes: Long): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> stringResource(Res.string.lobby_time_minutes, minutes.coerceAtLeast(1))
        rest == 0L -> stringResource(Res.string.lobby_time_hours, hours)
        else -> stringResource(Res.string.lobby_time_hours_minutes, hours, rest)
    }
}
