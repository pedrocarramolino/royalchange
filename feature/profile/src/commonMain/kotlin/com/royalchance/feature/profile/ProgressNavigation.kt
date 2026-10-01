package com.royalchance.feature.profile

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.royalchance.domain.economy.EconomyRepository
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlin.time.Clock

@Serializable
data object ProgressRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.progressRoutes() {
    subclass(ProgressRoute::class, ProgressRoute.serializer())
}

/** Nivel, estadísticas y logros (con el cobro de sus recompensas). */
fun EntryProviderScope<NavKey>.progressEntry(
    economyRepository: EconomyRepository,
    clock: Clock,
    timeZone: TimeZone,
) {
    entry<ProgressRoute> {
        ProgressScreen(viewModel = viewModel { ProgressViewModel(economyRepository, clock, timeZone) })
    }
}
