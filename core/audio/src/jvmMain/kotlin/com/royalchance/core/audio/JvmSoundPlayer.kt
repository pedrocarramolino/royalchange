package com.royalchance.core.audio

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

actual fun platformSoundPlayer(): SoundPlayer = JvmSoundPlayer()

/** Escritorio: un `Clip` de javax.sound por reproducción, abierto en un hilo propio. */
private class JvmSoundPlayer : SoundPlayer {

    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "royal-sound").apply { isDaemon = true } }
    private val pcm = ConcurrentHashMap<Sound, ByteArray>()
    private val format = AudioFormat(SoundSynth.SAMPLE_RATE.toFloat(), 16, 1, true, false)

    override fun play(sound: Sound) {
        executor.execute {
            // Sin dispositivo de audio (p. ej. un servidor), simplemente no suena.
            runCatching {
                val bytes = pcm.getOrPut(sound) { toPcm16(SoundSynth.render(sound)) }
                val clip = AudioSystem.getClip()
                clip.addLineListener { event -> if (event.type == LineEvent.Type.STOP) clip.close() }
                clip.open(format, bytes, 0, bytes.size)
                clip.start()
            }
        }
    }

    private fun toPcm16(samples: FloatArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, sample ->
            val value = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
            bytes[i * 2] = (value and 0xFF).toByte()
            bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
