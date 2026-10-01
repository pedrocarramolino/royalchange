package com.royalchance.feature.blackjack

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.core.ui.ProgressEventGate
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.game.GameSessionStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/** Mesa de Blackjack: pantalla inmersiva, sin la navegación principal. */
@Serializable
data object BlackjackRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.blackjackRoutes() {
    subclass(BlackjackRoute::class, BlackjackRoute.serializer())
}

class BlackjackDependencies(
    val authRepository: AuthRepository,
    val economyRepository: EconomyRepository,
    val sessions: GameSessionStore,
    val random: RandomGenerator,
)

/** @param eventGate retiene los avisos de progreso mientras el crupier destapa cartas. */
fun EntryProviderScope<NavKey>.blackjackEntry(
    dependencies: BlackjackDependencies,
    eventGate: ProgressEventGate,
    onBack: () -> Unit,
) {
    entry<BlackjackRoute> {
        BlackjackScreen(
            viewModel = viewModel {
                BlackjackViewModel(dependencies.authRepository, dependencies.economyRepository, dependencies.sessions, dependencies.random)
            },
            eventGate = eventGate,
            onBack = onBack,
        )
    }
}
