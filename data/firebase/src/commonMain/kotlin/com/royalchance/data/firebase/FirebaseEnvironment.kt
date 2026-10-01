package com.royalchance.data.firebase

/** Dónde se conectan los SDK de Firebase. */
sealed interface FirebaseEnvironment {
    /** Proyecto real en la nube. */
    data object Production : FirebaseEnvironment

    /**
     * Emuladores locales (`firebase emulators:start`). [host] es `127.0.0.1` en la web y el
     * escritorio, y `10.0.2.2` desde el emulador de Android (la máquina anfitriona).
     */
    data class Emulators(val host: String) : FirebaseEnvironment

    companion object {
        /** Puertos fijados en firebase.json. */
        const val AUTH_EMULATOR_PORT: Int = 9099
        const val FIRESTORE_EMULATOR_PORT: Int = 8085

        /** Entorno de la compilación: proyecto real salvo con -Proyalchance.firebase.emulators=true. */
        fun fromConfig(emulatorHost: String): FirebaseEnvironment =
            if (FirebaseProjectConfig.USE_EMULATORS) Emulators(emulatorHost) else Production
    }
}
