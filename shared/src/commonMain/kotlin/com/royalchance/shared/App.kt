package com.royalchance.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.royalchance.core.designsystem.orientation.OrientationGate
import com.royalchance.core.designsystem.motion.LocalReducedMotion
import com.royalchance.core.audio.SwitchableSoundPlayer
import com.royalchance.core.audio.LocalSoundPlayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.designsystem.adaptive.ProvideWindowWidthClass
import com.royalchance.core.designsystem.component.FullScreenLoading
import com.royalchance.core.designsystem.theme.RoyalChanceTheme
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.settings.ThemePreference
import com.royalchance.shared.navigation.AuthFlow
import com.royalchance.shared.navigation.CompleteProfileFlow
import com.royalchance.shared.navigation.MainFlow

/**
 * Punto de entrada de la UI en todas las plataformas.
 *
 * La pantalla raíz se deriva del estado de sesión: al iniciar o cerrar sesión no se navega a mano,
 * simplemente cambia el flujo mostrado. Así es imposible quedarse "dentro" del casino sin sesión.
 */
@Composable
fun App(graph: AppGraph) {
    val settings by graph.settingsRepository.settings.collectAsStateWithLifecycle()
    val authState by graph.authRepository.authState.collectAsStateWithLifecycle()

    val darkTheme = when (settings.theme) {
        ThemePreference.Dark -> true
        ThemePreference.Light -> false
        ThemePreference.System -> isSystemInDarkTheme()
    }

    // El reproductor consulta la preferencia en cada sonido: desactivarla silencia al instante.
    val currentSettings by rememberUpdatedState(settings)
    val sound = remember(graph) { SwitchableSoundPlayer(graph.soundPlayer) { currentSettings.soundEnabled } }

    CompositionLocalProvider(LocalSoundPlayer provides sound, LocalReducedMotion provides settings.reducedMotion) {
        RoyalChanceTheme(darkTheme = darkTheme) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                // Vertical en toda la app; las mesas piden horizontal (RequireLandscape).
                ProvideWindowWidthClass { OrientationGate {
                    AnimatedContent(
                        targetState = authState.toRootScreen(),
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                        label = "root",
                    ) { screen ->
                        when (screen) {
                            RootScreen.Loading -> FullScreenLoading()
                            RootScreen.SignedOut -> AuthFlow(graph)
                            RootScreen.CompleteProfile -> CompleteProfileFlow(graph)
                            RootScreen.Casino -> MainFlow(graph, user = (authState as? AuthState.SignedIn)?.user)
                        }
                    }
                } }
            }
        }
    }
}

private enum class RootScreen { Loading, SignedOut, CompleteProfile, Casino }

private fun AuthState.toRootScreen(): RootScreen = when (this) {
    AuthState.Loading -> RootScreen.Loading
    AuthState.SignedOut -> RootScreen.SignedOut
    is AuthState.SignedIn -> if (user.needsProfileCompletion) RootScreen.CompleteProfile else RootScreen.Casino
}
