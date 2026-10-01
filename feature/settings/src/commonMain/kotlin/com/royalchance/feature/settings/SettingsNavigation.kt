package com.royalchance.feature.settings

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.settings.SettingsRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder

@Serializable
data object SettingsRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.settingsRoutes() {
    subclass(SettingsRoute::class, SettingsRoute.serializer())
}

/**
 * Ajustes. Las acciones que llevan a otras features (los documentos legales) se reciben
 * como funciones: esta feature no conoce las rutas de las demás.
 */
fun EntryProviderScope<NavKey>.settingsEntry(
    authRepository: AuthRepository,
    settingsRepository: SettingsRepository,
    appVersion: String,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    entry<SettingsRoute> {
        SettingsScreen(
            viewModel = viewModel { SettingsViewModel(authRepository, settingsRepository) },
            appVersion = appVersion,
            onOpenTerms = onOpenTerms,
            onOpenPrivacy = onOpenPrivacy,
        )
    }
}
