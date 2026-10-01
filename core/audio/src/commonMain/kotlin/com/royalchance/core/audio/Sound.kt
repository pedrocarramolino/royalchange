package com.royalchance.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Efectos de sonido del casino. */
enum class Sound {
    /** Ficha sobre el tapete. */
    Chip,

    /** Carta repartida. */
    Card,

    /** Arranque de un giro (ruleta, tragaperras). */
    Spin,

    /** Un rodillo que se para. */
    ReelStop,

    /** Dados rodando. */
    Dice,

    /** Premio. */
    Win,

    /** Premio grande. */
    BigWin,

    /** Ronda perdida (suave). */
    Lose,
}

/** Reproduce efectos. Nunca lanza: si la plataforma no tiene audio, no suena nada. */
fun interface SoundPlayer {
    fun play(sound: Sound)
}

/** Reproductor que no hace nada (tests, previsualizaciones). */
val SilentSoundPlayer: SoundPlayer = SoundPlayer { }

/** Reproductor de la plataforma (Android, escritorio o navegador). */
expect fun platformSoundPlayer(): SoundPlayer

/** Reproductor que solo suena si [enabled] (la preferencia de sonido) es `true`. */
class SwitchableSoundPlayer(private val delegate: SoundPlayer, private val enabled: () -> Boolean) : SoundPlayer {
    override fun play(sound: Sound) {
        if (enabled()) delegate.play(sound)
    }
}

/** Reproductor de efectos de la app; la raíz lo proporciona según la preferencia de sonido. */
val LocalSoundPlayer = staticCompositionLocalOf { SilentSoundPlayer }

/** Atajo para pantallas: `val sound = rememberSoundPlayer()`; después `sound.play(Sound.Chip)`. */
@Composable
fun rememberSoundPlayer(): SoundPlayer = LocalSoundPlayer.current

/**
 * Sintetiza cada efecto como muestras PCM mono en [-1, 1]. Es determinista (el "ruido" sale de un
 * generador congruencial con semilla fija): el mismo efecto suena siempre igual y se puede probar.
 */
object SoundSynth {

    const val SAMPLE_RATE: Int = 44_100

    fun render(sound: Sound): FloatArray = when (sound) {
        Sound.Chip -> mix(0.45f) {
            tone(2_400.0, start = 0.0, length = 0.05, decay = 70.0, gain = 0.5)
            tone(3_300.0, start = 0.012, length = 0.05, decay = 80.0, gain = 0.35)
            noise(start = 0.0, length = 0.02, decay = 200.0, gain = 0.25, seed = 1)
        }
        Sound.Card -> mix(0.35f) {
            noise(start = 0.0, length = 0.11, decay = 35.0, gain = 0.9, seed = 2, attack = 0.012, lowPass = 0.35)
            tone(180.0, start = 0.05, length = 0.05, decay = 60.0, gain = 0.15)
        }
        Sound.Spin -> mix(0.3f) {
            // Bola que traquetea y se va frenando: clics cada vez más espaciados.
            var time = 0.0
            var gap = 0.035
            var index = 0
            while (time < 1.3) {
                tone(1_800.0 + (index % 3) * 150.0, start = time, length = 0.025, decay = 160.0, gain = 0.4)
                noise(start = time, length = 0.012, decay = 300.0, gain = 0.25, seed = 10 + index)
                time += gap
                gap *= 1.07
                index++
            }
        }
        Sound.ReelStop -> mix(0.45f) {
            tone(120.0, start = 0.0, length = 0.14, decay = 30.0, gain = 0.8)
            tone(240.0, start = 0.0, length = 0.08, decay = 45.0, gain = 0.3)
            noise(start = 0.0, length = 0.02, decay = 250.0, gain = 0.4, seed = 3)
        }
        Sound.Dice -> mix(0.4f) {
            val hits = listOf(0.0, 0.07, 0.15, 0.21, 0.32, 0.38, 0.5, 0.62)
            hits.forEachIndexed { index, time ->
                noise(start = time, length = 0.03, decay = 140.0, gain = 0.6 - index * 0.05, seed = 20 + index, lowPass = 0.5)
                tone(900.0 + (index * 137) % 500, start = time, length = 0.03, decay = 150.0, gain = 0.2)
            }
        }
        Sound.Win -> mix(0.3f) {
            listOf(523.25, 659.25, 783.99, 1_046.5).forEachIndexed { index, frequency ->
                bell(frequency, start = index * 0.085, length = 0.4, gain = 0.55)
            }
        }
        Sound.BigWin -> mix(0.3f) {
            val notes = listOf(523.25, 659.25, 783.99, 1_046.5, 783.99, 1_046.5, 1_318.5)
            notes.forEachIndexed { index, frequency ->
                bell(frequency, start = index * 0.08, length = 0.45, gain = 0.5)
            }
            // Acorde final sostenido.
            listOf(523.25, 659.25, 783.99, 1_046.5).forEach { bell(it, start = 0.6, length = 1.1, gain = 0.3, decay = 3.0) }
        }
        Sound.Lose -> mix(0.25f) {
            tone(392.0, start = 0.0, length = 0.18, decay = 12.0, gain = 0.4, attack = 0.01)
            tone(329.6, start = 0.16, length = 0.28, decay = 9.0, gain = 0.4, attack = 0.01)
        }
    }

    private inline fun mix(volume: Float, block: Mixer.() -> Unit): FloatArray {
        val mixer = Mixer()
        mixer.block()
        return mixer.render(volume)
    }

    /** Suma de voces sobre una pista; al final se normaliza al volumen pedido. */
    private class Mixer {
        private var samples = FloatArray(0)

        private fun ensure(length: Int) {
            if (length > samples.size) samples = samples.copyOf(length)
        }

        fun tone(frequency: Double, start: Double, length: Double, decay: Double, gain: Double, attack: Double = 0.002) {
            val from = (start * SAMPLE_RATE).toInt()
            val count = (length * SAMPLE_RATE).toInt()
            ensure(from + count)
            for (i in 0 until count) {
                val t = i.toDouble() / SAMPLE_RATE
                samples[from + i] += (sin(2 * PI * frequency * t) * envelope(t, attack, decay) * gain).toFloat()
            }
        }

        /** Campana: fundamental más armónicos que se apagan antes. */
        fun bell(frequency: Double, start: Double, length: Double, gain: Double, decay: Double = 7.0) {
            tone(frequency, start, length, decay, gain)
            tone(frequency * 2, start, length * 0.7, decay * 1.6, gain * 0.35)
            tone(frequency * 3, start, length * 0.4, decay * 2.5, gain * 0.15)
        }

        fun noise(start: Double, length: Double, decay: Double, gain: Double, seed: Int, attack: Double = 0.001, lowPass: Double = 1.0) {
            val from = (start * SAMPLE_RATE).toInt()
            val count = (length * SAMPLE_RATE).toInt()
            ensure(from + count)
            var state = seed * 1_103_515_245L + 12_345L
            var smoothed = 0.0
            for (i in 0 until count) {
                state = (state * 1_103_515_245L + 12_345L) and 0x7FFF_FFFFL
                val white = state.toDouble() / 0x7FFF_FFFFL * 2 - 1
                // Filtro paso bajo de un polo: más grave cuanto menor es lowPass.
                smoothed += (white - smoothed) * lowPass
                val t = i.toDouble() / SAMPLE_RATE
                samples[from + i] += (smoothed * envelope(t, attack, decay) * gain).toFloat()
            }
        }

        private fun envelope(t: Double, attack: Double, decay: Double): Double =
            min(1.0, t / max(attack, 1e-6)) * exp(-t * decay)

        fun render(volume: Float): FloatArray {
            val peak = samples.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
            if (peak == 0f) return samples
            val scale = volume / peak
            // Fundido de 5 ms al final para que no haya chasquido al cortar.
            val fade = min(samples.size, SAMPLE_RATE / 200)
            return FloatArray(samples.size) { i ->
                val tail = samples.size - i
                val fadeOut = if (tail < fade) tail.toFloat() / fade else 1f
                samples[i] * scale * fadeOut
            }
        }
    }
}
