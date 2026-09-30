package com.royalchance.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.royalchance.core.designsystem.component.EmptyState
import com.royalchance.core.designsystem.component.RoyalTopBar
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.feature.history.resources.Res
import com.royalchance.feature.history.resources.history_empty_message
import com.royalchance.feature.history.resources.history_empty_title
import com.royalchance.feature.history.resources.history_title
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import org.jetbrains.compose.resources.stringResource

@Serializable
data object HistoryRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.historyRoutes() {
    subclass(HistoryRoute::class, HistoryRoute.serializer())
}

fun EntryProviderScope<NavKey>.historyEntry() {
    entry<HistoryRoute> { HistoryScreen() }
}

/** El historial y las estadísticas llegan en la Fase 11; por ahora, estado vacío. */
@Composable
internal fun HistoryScreen() {
    Column {
        RoyalTopBar(title = stringResource(Res.string.history_title), windowInsets = WindowInsets(0))
        EmptyState(
            icon = RoyalIcons.History,
            title = stringResource(Res.string.history_empty_title),
            message = stringResource(Res.string.history_empty_message),
        )
    }
}
