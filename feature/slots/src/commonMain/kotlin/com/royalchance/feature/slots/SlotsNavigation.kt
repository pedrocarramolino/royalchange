package com.royalchance.feature.slots

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

/** Tragaperras: pantalla inmersiva, sin la navegación principal. */
@Serializable
data object SlotsRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.slotsRoutes() {
    subclass(SlotsRoute::class, SlotsRoute.serializer())
}

class SlotsDependencies(
    val authRepository: AuthRepository,
    val economyRepository: EconomyRepository,
    val sessions: GameSessionStore,
    val random: RandomGenerator,
)

/** @param eventGate retiene los avisos de progreso mientras giran los rodillos. */
fun EntryProviderScope<NavKey>.slotsEntry(
    dependencies: SlotsDependencies,
    eventGate: ProgressEventGate,
    onBack: () -> Unit,
) {
    entry<SlotsRoute> {
        SlotsScreen(
            viewModel = viewModel {
                SlotsViewModel(dependencies.authRepository, dependencies.economyRepository, dependencies.sessions, dependencies.random)
            },
            eventGate = eventGate,
            onBack = onBack,
        )
    }
}
