package com.royalchance.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.royalchance.core.designsystem.component.EmptyState
import com.royalchance.core.designsystem.component.RoyalTopBar
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.feature.profile.resources.Res
import com.royalchance.feature.profile.resources.progress_empty_message
import com.royalchance.feature.profile.resources.progress_empty_title
import com.royalchance.feature.profile.resources.progress_title
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import org.jetbrains.compose.resources.stringResource

@Serializable
data object ProgressRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.progressRoutes() {
    subclass(ProgressRoute::class, ProgressRoute.serializer())
}

fun EntryProviderScope<NavKey>.progressEntry() {
    entry<ProgressRoute> { ProgressScreen() }
}

/** Niveles, racha y logros llegan en la Fase 6; por ahora explica qué aparecerá aquí. */
@Composable
internal fun ProgressScreen() {
    Column {
        RoyalTopBar(title = stringResource(Res.string.progress_title), windowInsets = WindowInsets(0))
        EmptyState(
            icon = RoyalIcons.Trophy,
            title = stringResource(Res.string.progress_empty_title),
            message = stringResource(Res.string.progress_empty_message),
        )
    }
}
