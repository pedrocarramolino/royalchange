package com.royalchance.feature.poker

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

/** Mesa de póker: pantalla inmersiva, sin la navegación principal. */
@Serializable
data object PokerRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.pokerRoutes() {
    subclass(PokerRoute::class, PokerRoute.serializer())
}

class PokerDependencies(
    val authRepository: AuthRepository,
    val economyRepository: EconomyRepository,
    val sessions: GameSessionStore,
    val random: RandomGenerator,
)

/** @param eventGate retiene los avisos de progreso hasta que termina la mano. */
fun EntryProviderScope<NavKey>.pokerEntry(
    dependencies: PokerDependencies,
    eventGate: ProgressEventGate,
    onBack: () -> Unit,
) {
    entry<PokerRoute> {
        PokerScreen(
            viewModel = viewModel {
                PokerViewModel(dependencies.authRepository, dependencies.economyRepository, dependencies.sessions, dependencies.random)
            },
            eventGate = eventGate,
            onBack = onBack,
        )
    }
}
