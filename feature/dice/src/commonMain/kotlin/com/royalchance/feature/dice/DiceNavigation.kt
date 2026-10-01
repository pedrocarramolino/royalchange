package com.royalchance.feature.dice

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

/** Mesa de dados: pantalla inmersiva, sin la navegación principal. */
@Serializable
data object DiceRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.diceRoutes() {
    subclass(DiceRoute::class, DiceRoute.serializer())
}

class DiceDependencies(
    val authRepository: AuthRepository,
    val economyRepository: EconomyRepository,
    val sessions: GameSessionStore,
    val random: RandomGenerator,
)

/** @param eventGate retiene los avisos de progreso mientras ruedan los dados. */
fun EntryProviderScope<NavKey>.diceEntry(
    dependencies: DiceDependencies,
    eventGate: ProgressEventGate,
    onBack: () -> Unit,
) {
    entry<DiceRoute> {
        DiceScreen(
            viewModel = viewModel {
                DiceViewModel(dependencies.authRepository, dependencies.economyRepository, dependencies.sessions, dependencies.random)
            },
            eventGate = eventGate,
            onBack = onBack,
        )
    }
}
