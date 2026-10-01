package com.royalchance.core.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class SoundSynthTest {

    @Test
    fun everySoundIsShortNormalisedAndEndsInSilence() {
        Sound.entries.forEach { sound ->
            val samples = SoundSynth.render(sound)
            val seconds = samples.size.toDouble() / SoundSynth.SAMPLE_RATE
            assertTrue(seconds in 0.03..2.0, "$sound dura $seconds s")
            val peak = samples.maxOf { abs(it) }
            assertTrue(peak in 0.2f..0.5f, "$sound tiene un pico de $peak")
            // El último milisegundo es casi silencio: sin chasquido al terminar.
            assertTrue(samples.takeLast(44).all { abs(it) < 0.02f }, "$sound termina de golpe")
        }
    }

    @Test
    fun soundsAreDeterministic() {
        Sound.entries.forEach { assertContentEquals(SoundSynth.render(it), SoundSynth.render(it)) }
    }

    @Test
    fun theSwitchRespectsThePreference() {
        val played = mutableListOf<Sound>()
        var enabled = false
        val player = SwitchableSoundPlayer({ played += it }) { enabled }

        player.play(Sound.Chip)
        enabled = true
        player.play(Sound.Win)

        assertContentEquals(listOf(Sound.Win), played)
    }
}
