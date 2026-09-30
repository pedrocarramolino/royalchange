package com.royalchance.feature.lobby

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.royalchance.domain.auth.AuthRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder

@Serializable
data object LobbyRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.lobbyRoutes() {
    subclass(LobbyRoute::class, LobbyRoute.serializer())
}

fun EntryProviderScope<NavKey>.lobbyEntry(
    authRepository: AuthRepository,
    onCreateAccount: () -> Unit,
) {
    entry<LobbyRoute> {
        LobbyScreen(viewModel = viewModel { LobbyViewModel(authRepository) }, onCreateAccount = onCreateAccount)
    }
}
