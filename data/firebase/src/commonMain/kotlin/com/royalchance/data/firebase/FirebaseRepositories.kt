package com.royalchance.data.firebase

import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.data.firebase.gateway.AuthGateway
import com.royalchance.data.firebase.gateway.PlayerStore
import com.royalchance.data.firebase.gateway.WalletStore
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.history.LedgerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** Repositorios respaldados por Firebase. Comparten la misma instancia de Auth y Firestore. */
class FirebaseRepositories internal constructor(
    val auth: AuthRepository,
    val economy: EconomyRepository,
    /** Lectura del libro contable (historial y estadísticas). */
    val ledger: LedgerSource,
) {
    internal companion object {
        fun create(
            authGateway: AuthGateway,
            playerStore: PlayerStore,
            walletStore: WalletStore,
            clock: Clock,
            timeZone: TimeZone,
            random: RandomGenerator,
            scope: CoroutineScope,
        ): FirebaseRepositories {
            val auth = FirebaseAuthRepository(authGateway, playerStore, clock, scope)
            val economy = FirebaseEconomyRepository(auth, walletStore, clock, timeZone, random, scope)
            return FirebaseRepositories(auth = auth, economy = economy, ledger = economy)
        }
    }
}
