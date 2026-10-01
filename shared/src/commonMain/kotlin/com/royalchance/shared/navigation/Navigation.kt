package com.royalchance.shared.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import com.royalchance.feature.auth.navigation.authRoutes
import com.royalchance.feature.blackjack.blackjackRoutes
import com.royalchance.feature.dice.diceRoutes
import com.royalchance.feature.roulette.rouletteRoutes
import com.royalchance.feature.slots.slotsRoutes
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
            blackjackRoutes()
            rouletteRoutes()
            slotsRoutes()
            diceRoutes()
            lobbyRoutes()
            progressRoutes()
            historyRoutes()
            settingsRoutes()
        }
    }
}
