package com.royalchance.core.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.royalchance.core.audio.LocalSoundPlayer
import com.royalchance.core.audio.Sound
import com.royalchance.core.audio.SoundPlayer
import com.royalchance.core.designsystem.graphics.SuitShape
import com.royalchance.core.designsystem.motion.LocalReducedMotion
import com.royalchance.core.designsystem.theme.RoyalChanceTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ComponentsTest {

    @Test
    fun aChipBetsAndSounds() = runComposeUiTest {
        val sounds = mutableListOf<Sound>()
        var clicks = 0
        setContent {
            CompositionLocalProvider(LocalSoundPlayer provides SoundPlayer { sounds += it }) {
                RoyalChanceTheme {
                    BetChip(value = 100, description = "Añadir 100 fichas", onClick = { clicks++ }, selected = true)
                }
            }
        }

        onNodeWithContentDescription("Añadir 100 fichas").assertIsSelected().performClick()

        assertEquals(1, clicks)
        assertEquals(listOf(Sound.Chip), sounds)
    }

    @Test
    fun aDisabledChipDoesNothing() = runComposeUiTest {
        var clicks = 0
        setContent {
            RoyalChanceTheme { BetChip(value = 5_000, description = "Añadir 5.000", onClick = { clicks++ }, enabled = false) }
        }

        onNodeWithContentDescription("Añadir 5.000").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun chipLabelsAreShort() {
        assertEquals("10", chipLabel(10))
        assertEquals("1K", chipLabel(1_000))
        assertEquals("2,5K", chipLabel(2_500))
        assertEquals("1260", chipLabel(1_260))
        assertEquals("25K", chipLabel(25_400))
    }

    @Test
    fun aCardDescribesItselfOnlyWhenFaceUp() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                RoyalChanceTheme {
                    PlayingCard(rank = "A", suit = SuitShape.Spades, description = "As de picas")
                }
            }
        }

        onNodeWithContentDescription("As de picas").assertExists()
    }

    @Test
    fun buttonsRunTheirAction() = runComposeUiTest {
        var primary = 0
        var secondary = 0
        setContent {
            RoyalChanceTheme {
                androidx.compose.foundation.layout.Column {
                    RoyalPrimaryButton(text = "Repartir", onClick = { primary++ })
                    RoyalSecondaryButton(text = "Retirarse", onClick = { secondary++ })
                    RoyalPrimaryButton(text = "Esperando", onClick = { primary++ }, loading = true)
                }
            }
        }

        onNodeWithText("Repartir").performClick()
        onNodeWithText("Retirarse").performClick()
        // Mientras carga no admite pulsaciones (y anuncia su texto como descripción).
        onNodeWithContentDescription("Esperando").assertIsNotEnabled()

        assertEquals(1, primary)
        assertEquals(1, secondary)
    }
}
