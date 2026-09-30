package com.royalchance.shared.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import com.royalchance.feature.auth.navigation.authRoutes
import com.royalchance.feature.history.historyRoutes
import com.royalchance.feature.lobby.lobbyRoutes
import com.royalchance.feature.profile.progressRoutes
import com.royalchance.feature.settings.settingsRoutes
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * Configuración de guardado de las pilas de navegación. Fuera de Android no hay reflexión, así que
 * cada ruta se registra explícitamente; cada feature aporta las suyas.
 */
internal val NavigationConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            authRoutes()
            lobbyRoutes()
            progressRoutes()
            historyRoutes()
            settingsRoutes()
        }
    }
}
