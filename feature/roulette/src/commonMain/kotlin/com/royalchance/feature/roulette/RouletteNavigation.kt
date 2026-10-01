package com.royalchance.feature.roulette

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

/** Mesa de ruleta: pantalla inmersiva, sin la navegación principal. */
@Serializable
data object RouletteRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.rouletteRoutes() {
    subclass(RouletteRoute::class, RouletteRoute.serializer())
}

class RouletteDependencies(
    val authRepository: AuthRepository,
    val economyRepository: EconomyRepository,
    val sessions: GameSessionStore,
    val random: RandomGenerator,
)

/** @param eventGate retiene los avisos de progreso mientras gira la bola. */
fun EntryProviderScope<NavKey>.rouletteEntry(
    dependencies: RouletteDependencies,
    eventGate: ProgressEventGate,
    onBack: () -> Unit,
) {
    entry<RouletteRoute> {
        RouletteScreen(
            viewModel = viewModel {
                RouletteViewModel(dependencies.authRepository, dependencies.economyRepository, dependencies.sessions, dependencies.random)
            },
            eventGate = eventGate,
            onBack = onBack,
        )
    }
}
