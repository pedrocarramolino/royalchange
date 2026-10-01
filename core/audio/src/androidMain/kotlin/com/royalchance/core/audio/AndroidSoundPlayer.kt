package com.royalchance.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors

actual fun platformSoundPlayer(): SoundPlayer = AndroidSoundPlayer()

/**
 * Android: un `AudioTrack` estático por efecto, creado la primera vez que suena. Volver a sonar
 * reinicia la pista. Todo ocurre en un hilo propio para no bloquear la interfaz.
 */
private class AndroidSoundPlayer : SoundPlayer {

    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "royal-sound").apply { isDaemon = true } }
    private val tracks = HashMap<Sound, AudioTrack>()

    override fun play(sound: Sound) {
        executor.execute {
            runCatching {
                val track = tracks.getOrPut(sound) { createTrack(SoundSynth.render(sound)) }
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                track.reloadStaticData()
                track.play()
            }
        }
    }

    private fun createTrack(samples: FloatArray): AudioTrack {
        val pcm = ShortArray(samples.size) { i -> (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SoundSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }
}
