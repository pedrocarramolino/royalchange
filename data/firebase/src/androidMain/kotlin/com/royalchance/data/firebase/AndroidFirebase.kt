package com.royalchance.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.royalchance.domain.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock

/** Punto de entrada de Firebase en Android. */
object AndroidFirebase {

    /** Crea el repositorio de autenticación sobre Firebase. */
    fun authRepository(
        context: Context,
        environment: FirebaseEnvironment,
        scope: CoroutineScope,
        clock: Clock = Clock.System,
    ): AuthRepository {
        check(FirebaseProjectConfig.ANDROID_APP_ID.isNotBlank()) {
            "La app Android no está registrada en Firebase: añade androidAppId en firebase.properties " +
                "o compila con -Proyalchance.firebase.emulators=true (ver README)."
        }
        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context, options())
        val auth = FirebaseAuth.getInstance(app)
        val firestore = FirebaseFirestore.getInstance(app)
        if (environment is FirebaseEnvironment.Emulators) {
            // Debe hacerse antes de cualquier otra llamada a los SDK.
            auth.useEmulator(environment.host, FirebaseEnvironment.AUTH_EMULATOR_PORT)
            firestore.useEmulator(environment.host, FirebaseEnvironment.FIRESTORE_EMULATOR_PORT)
        }
        return FirebaseAuthRepository(
            auth = AndroidAuthGateway(auth),
            players = AndroidPlayerStore(firestore),
            clock = clock,
            scope = scope,
        )
    }

    // Inicialización manual: no hace falta google-services.json ni su plugin de Gradle.
    private fun options(): FirebaseOptions = FirebaseOptions.Builder()
        .setProjectId(FirebaseProjectConfig.PROJECT_ID)
        .setApplicationId(FirebaseProjectConfig.ANDROID_APP_ID)
        .setApiKey(FirebaseProjectConfig.ANDROID_API_KEY)
        .setGcmSenderId(FirebaseProjectConfig.MESSAGING_SENDER_ID)
        .build()
}
