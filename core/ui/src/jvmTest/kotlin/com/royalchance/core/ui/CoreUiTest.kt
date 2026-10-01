package com.royalchance.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import com.royalchance.core.designsystem.motion.LocalReducedMotion
import com.royalchance.core.designsystem.theme.RoyalChanceTheme
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.progression.ProgressEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CoreUiTest {

    @Test
    fun theBalanceIsAnnouncedWithItsUnit() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                RoyalChanceTheme { ChipBalance(Chips(25_450)) }
            }
        }

        onNodeWithContentDescription("25.450 fichas").assertExists()
    }

    @Test
    fun progressEventsWaitWhileATableHoldsThem() = runComposeUiTest {
        val events = MutableSharedFlow<ProgressEvent>(extraBufferCapacity = 1)
        val gate = ProgressEventGate().apply { held = true }
        setContent { RoyalChanceTheme { ProgressEventHost(events, gate) } }

        events.tryEmit(ProgressEvent.LevelUp(5))
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        // Retenido: la mesa aún anima el resultado.
        assertTrue(onAllNodesWithText("¡Subes al nivel 5!").fetchSemanticsNodes().isEmpty())

        gate.held = false
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("¡Subes al nivel 5!").fetchSemanticsNodes().isNotEmpty() }
    }
}
