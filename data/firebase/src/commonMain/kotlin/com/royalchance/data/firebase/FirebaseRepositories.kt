package com.royalchance.data.firebase

import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.data.firebase.gateway.AuthGateway
import com.royalchance.data.firebase.gateway.PlayerStore
import com.royalchance.data.firebase.gateway.WalletStore
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.economy.EconomyRepository
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock

/** Repositorios respaldados por Firebase. Comparten la misma instancia de Auth y Firestore. */
class FirebaseRepositories internal constructor(
    val auth: AuthRepository,
    val economy: EconomyRepository,
) {
    internal companion object {
        fun create(
            authGateway: AuthGateway,
            playerStore: PlayerStore,
            walletStore: WalletStore,
            clock: Clock,
            random: RandomGenerator,
            scope: CoroutineScope,
        ): FirebaseRepositories {
            val auth = FirebaseAuthRepository(authGateway, playerStore, clock, scope)
            return FirebaseRepositories(
                auth = auth,
                economy = FirebaseEconomyRepository(auth, walletStore, clock, random, scope),
            )
        }
    }
}
