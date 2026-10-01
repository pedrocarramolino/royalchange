package com.royalchance.feature.auth.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.royalchance.core.common.locale.deviceRegionCode
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.validation.RegistrationMode
import com.royalchance.domain.auth.validation.RegistrationValidator
import com.royalchance.domain.profile.Countries
import com.royalchance.feature.auth.forgot.ForgotPasswordScreen
import com.royalchance.feature.auth.forgot.ForgotPasswordViewModel
import com.royalchance.feature.auth.legal.LegalDocument
import com.royalchance.feature.auth.legal.LegalDocumentScreen
import com.royalchance.feature.auth.login.LoginScreen
import com.royalchance.feature.auth.login.LoginViewModel
import com.royalchance.feature.auth.register.RegisterScreen
import com.royalchance.feature.auth.register.RegisterViewModel
import com.royalchance.feature.auth.welcome.WelcomeScreen
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlin.time.Clock

/** Rutas de acceso. Son serializables para restaurar la pila de navegación tras recrear la app. */
sealed interface AuthRoute : NavKey {
    @Serializable
    data object Welcome : AuthRoute

    @Serializable
    data object Login : AuthRoute

    @Serializable
    data object Register : AuthRoute

    @Serializable
    data object CompleteProfile : AuthRoute

    @Serializable
    data class ForgotPassword(val email: String = "") : AuthRoute

    @Serializable
    data class Legal(val document: LegalDocument) : AuthRoute
}

/** Registra las rutas para la serialización polimórfica que exige Navigation 3 fuera de Android. */
fun PolymorphicModuleBuilder<NavKey>.authRoutes() {
    subclass(AuthRoute.Welcome::class, AuthRoute.Welcome.serializer())
    subclass(AuthRoute.Login::class, AuthRoute.Login.serializer())
    subclass(AuthRoute.Register::class, AuthRoute.Register.serializer())
    subclass(AuthRoute.CompleteProfile::class, AuthRoute.CompleteProfile.serializer())
    subclass(AuthRoute.ForgotPassword::class, AuthRoute.ForgotPassword.serializer())
    subclass(AuthRoute.Legal::class, AuthRoute.Legal.serializer())
}

/** Dependencias de la feature de acceso, construidas en la raíz de composición. */
class AuthDependencies(
    val authRepository: AuthRepository,
    val clock: Clock,
    val timeZone: TimeZone,
)

/**
 * Pantallas de acceso. Tras iniciar sesión o registrarse no hace falta navegar: la raíz de la app
 * observa el estado de sesión y cambia de flujo sola.
 */
fun EntryProviderScope<NavKey>.authEntries(
    dependencies: AuthDependencies,
    navigate: (NavKey) -> Unit,
    back: () -> Unit,
) {
    val openLegal: (LegalDocument) -> Unit = { navigate(AuthRoute.Legal(it)) }

    entry<AuthRoute.Welcome> {
        WelcomeScreen(
            onCreateAccount = { navigate(AuthRoute.Register) },
            onSignIn = { navigate(AuthRoute.Login) },
            onOpenLegal = openLegal,
        )
    }
    entry<AuthRoute.Login> {
        LoginScreen(
            viewModel = viewModel { LoginViewModel(dependencies.authRepository) },
            onBack = back,
            onForgotPassword = { email -> navigate(AuthRoute.ForgotPassword(email)) },
            onCreateAccount = { navigate(AuthRoute.Register) },
        )
    }
    entry<AuthRoute.Register> {
        RegisterScreen(
            viewModel = viewModel { registerViewModel(dependencies, RegistrationMode.EmailAccount) },
            onBack = back,
            onOpenLegal = openLegal,
        )
    }
    entry<AuthRoute.CompleteProfile> {
        RegisterScreen(
            viewModel = viewModel { registerViewModel(dependencies, RegistrationMode.CompleteProfile) },
            onBack = null,
            onOpenLegal = openLegal,
        )
    }
    entry<AuthRoute.ForgotPassword> { route ->
        ForgotPasswordScreen(
            viewModel = viewModel { ForgotPasswordViewModel(dependencies.authRepository, route.email) },
            onBack = back,
        )
    }
    legalEntries(back)
}

/** Términos y privacidad, también accesibles desde los ajustes con la sesión iniciada. */
fun EntryProviderScope<NavKey>.legalEntries(back: () -> Unit) {
    entry<AuthRoute.Legal> { route ->
        LegalDocumentScreen(document = route.document, onBack = back)
    }
}

private fun registerViewModel(dependencies: AuthDependencies, mode: RegistrationMode) = RegisterViewModel(
    authRepository = dependencies.authRepository,
    validator = RegistrationValidator(dependencies.clock, dependencies.timeZone),
    mode = mode,
    clock = dependencies.clock,
    timeZone = dependencies.timeZone,
    defaultCountry = deviceRegionCode()?.takeIf { it in Countries.isoCodes },
)
