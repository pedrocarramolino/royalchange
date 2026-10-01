package com.royalchance.shared

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.royalchance.core.audio.SilentSoundPlayer
import com.royalchance.core.testing.random.TestRandomGenerator
import com.royalchance.data.auth.InMemoryAuthRepository
import com.royalchance.data.economy.InMemoryEconomyRepository
import com.royalchance.data.games.KeyValueGameSessionStore
import com.royalchance.data.history.LedgerHistoryRepository
import com.royalchance.data.settings.InMemoryKeyValueStore
import com.royalchance.data.settings.InMemorySettingsRepository
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import com.royalchance.domain.economy.LedgerEntryKind
import com.royalchance.domain.economy.WalletState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * La app completa (navegación, pantallas y ViewModels) con repositorios en memoria: lo que hace
 * un jugador, de principio a fin.
 */
@OptIn(ExperimentalTestApi::class)
class AppTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val auth = InMemoryAuthRepository(simulatedLatency = Duration.ZERO)
    private val economy = InMemoryEconomyRepository(auth, scope)
    private val settings = InMemorySettingsRepository()
    private val graph = AppGraph(
        authRepository = auth,
        economyRepository = economy,
        settingsRepository = settings,
        gameSessions = KeyValueGameSessionStore(InMemoryKeyValueStore()),
        historyRepository = LedgerHistoryRepository(auth, economy, InMemoryKeyValueStore()),
        random = TestRandomGenerator(seed = 42),
        soundPlayer = SilentSoundPlayer,
    )

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun registerAna() = runBlocking {
        val profile = PlayerProfile("Ana", AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", Clock.System.now()))
        auth.register(NewAccount("ana@example.com", "Secreto123", profile))
        auth.signOut()
    }

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 10_000) {
        try {
            waitUntil(conditionDescription = "texto \"$text\"", timeoutMillis = timeoutMillis) {
                onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            // Qué había en pantalla, para entender el fallo.
            throw AssertionError("No apareció \"$text\". En pantalla:\n" + onRoot().printToString(), e)
        }
    }

    private fun ComposeUiTest.field(label: String): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasText(label, substring = true))

    private fun ComposeUiTest.button(text: String): SemanticsNodeInteraction =
        onAllNodesWith(hasClickAction() and (hasText(text) or hasContentDescription(text, substring = true))).onFirst()

    private fun ComposeUiTest.onAllNodesWith(matcher: SemanticsMatcher) = onAllNodes(matcher)

    private fun ComposeUiTest.anyText(vararg texts: String): Boolean =
        texts.any { onAllNodesWithText(it, substring = true).fetchSemanticsNodes().isNotEmpty() }

    /** Entra con la sesión ya iniciada y abre la mesa de [game] desde el lobby. */
    private fun ComposeUiTest.openGame(game: String) {
        runBlocking {
            val profile = PlayerProfile("Ana", AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", Clock.System.now()))
            auth.register(NewAccount("ana@example.com", "Secreto123", profile))
        }
        setContent { App(graph) }
        waitForText("Hola, Ana")
        waitUntil(timeoutMillis = 10_000) { economy.wallet.value is WalletState.Ready }
        // La lista del lobby es perezosa: la tarjeta solo existe cuando se desplaza hasta ella.
        val card = hasContentDescription("$game.", substring = true) and hasClickAction()
        onNode(hasScrollToNodeAction()).performScrollToNode(card)
        onNode(card).performClick()
    }

    private fun ledgerKinds(): List<LedgerEntryKind> {
        val id = assertIs<AuthState.SignedIn>(auth.authState.value).user.id
        return economy.ledger(id).map { it.kind }
    }

    @Test
    fun theDailyBonusIsClaimedFromTheLobbyAndProgressIsShown() = runComposeUiTest {
        runBlocking {
            val profile = PlayerProfile("Ana", AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", Clock.System.now()))
            auth.register(NewAccount("ana@example.com", "Secreto123", profile))
        }
        setContent { App(graph) }
        waitForText("Recoger bono")

        button("Recoger bono").performClick()

        waitUntil(timeoutMillis = 10_000) { (economy.wallet.value as? WalletState.Ready)?.wallet?.balance?.amount == 10_500L }
        button("Progreso").performClick()
        waitForText("Nivel 1")
    }

    @Test
    fun aHandOfBlackjackIsPlayedAndSettled() = runComposeUiTest {
        openGame("Blackjack")
        waitForText("Repartir")
        button("Repartir").performClick()

        waitUntil(timeoutMillis = 10_000) { anyText("Plantarse", "en esta ronda") }
        if (anyText("Plantarse")) button("Plantarse").performClick()

        waitForText("en esta ronda")
        assertEquals(LedgerEntryKind.Settlement, ledgerKinds().last())
    }

    @Test
    fun aRouletteSpinIsPaidOnce() = runComposeUiTest {
        openGame("Ruleta")
        waitForText("Haz tus apuestas")
        button("Apostar a rojo").performScrollTo().performClick()
        waitForText("Apuesta total")
        button("Girar").performClick()

        waitUntil(timeoutMillis = 15_000) { anyText("Ganas", "Pierdes", "Recuperas") }
        assertEquals(listOf(LedgerEntryKind.Welcome, LedgerEntryKind.InstantRound), ledgerKinds())
    }

    @Test
    fun theSlotMachineSpins() = runComposeUiTest {
        openGame("Slots")
        waitForText("Elige tu apuesta y gira")
        button("Girar").performClick()

        waitUntil(timeoutMillis = 15_000) { anyText("¡Ganas", "Recuperas", "Sin premio") }
        assertEquals(LedgerEntryKind.InstantRound, ledgerKinds().last())
    }

    @Test
    fun theDiceAreRolled() = runComposeUiTest {
        openGame("Dados")
        waitForText("Haz tus apuestas")
        button("Apostar a mayor").performScrollTo().performClick()
        button("Tirar").performClick()

        waitUntil(timeoutMillis = 15_000) { anyText("· suma") }
        assertEquals(LedgerEntryKind.InstantRound, ledgerKinds().last())
    }

    @Test
    fun aPokerHandCanBeFolded() = runComposeUiTest {
        openGame("Póker")
        waitForText("Sentarse")
        button("Sentarse").performClick()

        // Le toca al jugador o la mano termina sin él (si no le tocaba poner ciega y todos pasaron).
        waitUntil(timeoutMillis = 20_000) { anyText("Retirarse", "Repartir") }
        if (anyText("Retirarse")) button("Retirarse").performClick()

        waitUntil(timeoutMillis = 20_000) { anyText("Repartir", "La mano sigue sin ti") }
        val wallet = assertIs<WalletState.Ready>(economy.wallet.value).wallet
        // Lo que puso en el bote ya está liquidado: no queda ninguna ronda abierta.
        waitUntil(timeoutMillis = 10_000) { (economy.wallet.value as WalletState.Ready).wallet.openRound == null }
        assertEquals(true, wallet.balance.amount <= 10_000)
    }

    @Test
    fun theHistoryShowsWhatWasPlayed() = runComposeUiTest {
        openGame("Ruleta")
        waitForText("Haz tus apuestas")
        button("Apostar a negro").performScrollTo().performClick()
        button("Girar").performClick()
        waitUntil(timeoutMillis = 15_000) { anyText("Ganas", "Pierdes", "Recuperas") }
        button("Volver al casino").performClick()

        button("Historial").performClick()

        waitForText("POR JUEGO")
        waitForText("1 rondas")
        // Cada fila del historial se anuncia completa a los lectores de pantalla.
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasContentDescription("Ruleta. apuesta 100 fichas", substring = true)).fetchSemanticsNodes().isNotEmpty() &&
                onAllNodes(hasContentDescription("Fichas de bienvenida", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeUiTest.signInFromWelcome() {
        waitForText("Bienvenido a la mesa")
        button("Iniciar sesión").performClick()
        waitForText("Email")
        field("Email").performTextInput("ana@example.com")
        field("Contraseña").performTextInput("Secreto123")
        onAllNodes(hasClickAction() and hasText("Iniciar sesión")).fetchSemanticsNodes().let { nodes ->
            // El último "Iniciar sesión" es el botón del formulario (el primero es el título).
            onAllNodes(hasClickAction() and hasText("Iniciar sesión"))[nodes.lastIndex].performClick()
        }
        waitForText("Hola, Ana")
    }

    @Test
    fun aPlayerSignsInAndSeesTheCasino() = runComposeUiTest {
        registerAna()
        setContent { App(graph) }

        signInFromWelcome()

        waitForText("10.000")
        val wallet = economy.wallet.value
        assertIs<WalletState.Ready>(wallet)
        assertEquals(10_000, wallet.wallet.balance.amount)
    }

    @Test
    fun aNewPlayerRegistersAndGetsTheWelcomeChips() = runComposeUiTest {
        setContent { App(graph) }
        waitForText("Bienvenido a la mesa")
        button("Crear cuenta").performClick()
        waitForText("Tu cuenta guarda tus fichas")

        field("Alias").performScrollTo().performTextInput("Nuevo_Jugador")
        field("Email").performScrollTo().performTextInput("nuevo@example.com")
        field("Contraseña").performScrollTo().performTextInput("Ruleta-Prueba-2026")
        field("Repite la contraseña").performScrollTo().performTextInput("Ruleta-Prueba-2026")

        // Fecha de nacimiento: se abre el selector y se escribe la fecha.
        onNode(hasText("Fecha de nacimiento") and hasClickAction()).performScrollTo().performClick()
        waitForText("Seleccionar fecha")
        onAllNodes(hasSetTextAction() and hasText("Fecha", substring = true)).onFirst().performTextInput("15061990")
        button("Aceptar").performClick()

        onNode(hasText("Acepto los", substring = true) and hasClickAction()).performScrollTo().performClick()
        onNode(hasText("Entiendo que las fichas", substring = true) and hasClickAction()).performScrollTo().performClick()
        onAllNodes(hasClickAction() and hasText("Crear cuenta")).let { buttons ->
            buttons[buttons.fetchSemanticsNodes().lastIndex].performScrollTo().performClick()
        }

        waitForText("Hola, Nuevo_Jugador", timeoutMillis = 15_000)
        waitUntil(timeoutMillis = 10_000) { (economy.wallet.value as? WalletState.Ready)?.wallet?.balance?.amount == 10_000L }
    }

    @Test
    fun theSoundSettingIsSaved() = runComposeUiTest {
        registerAna()
        setContent { App(graph) }
        signInFromWelcome()

        button("Ajustes").performClick()
        waitForText("Sonido")
        onNode(hasText("Sonido") and hasClickAction()).performScrollTo().performClick()

        waitUntil(timeoutMillis = 5_000) { !settings.settings.value.soundEnabled }
    }
}
