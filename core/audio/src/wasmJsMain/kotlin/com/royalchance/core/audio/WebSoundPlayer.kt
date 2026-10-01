@file:OptIn(ExperimentalWasmJsInterop::class)

package com.royalchance.core.audio

actual fun platformSoundPlayer(): SoundPlayer = WebSoundPlayer()

/**
 * Navegador: Web Audio. El `AudioContext` se crea en la primera reproducción, que llega tras un
 * toque del jugador (los navegadores, y sobre todo Safari, no dejan sonar antes de un gesto). Cada
 * efecto se convierte una vez en un `AudioBuffer`.
 */
private class WebSoundPlayer : SoundPlayer {

    private var context: JsAny? = null
    private val buffers = HashMap<Sound, JsAny>()

    override fun play(sound: Sound) {
        try {
            val audio = context ?: createAudioContext()?.also { context = it } ?: return
            resumeIfSuspended(audio)
            val buffer = buffers.getOrPut(sound) { toBuffer(audio, SoundSynth.render(sound)) }
            playBuffer(audio, buffer)
        } catch (_: Throwable) {
            // Sin Web Audio o bloqueado por el navegador: no suena.
        }
    }

    private fun toBuffer(audio: JsAny, samples: FloatArray): JsAny {
        val buffer = createBuffer(audio, samples.size, SoundSynth.SAMPLE_RATE)
        val channel = channelData(buffer)
        for (i in samples.indices) setSample(channel, i, samples[i].toDouble())
        return buffer
    }
}

private fun createAudioContext(): JsAny? =
    js("(typeof AudioContext !== 'undefined') ? new AudioContext() : ((typeof webkitAudioContext !== 'undefined') ? new webkitAudioContext() : null)")

private fun resumeIfSuspended(audio: JsAny): Unit = js("{ if (audio.state === 'suspended') { audio.resume(); } }")

private fun createBuffer(audio: JsAny, length: Int, sampleRate: Int): JsAny = js("audio.createBuffer(1, length, sampleRate)")

private fun channelData(buffer: JsAny): JsAny = js("buffer.getChannelData(0)")

private fun setSample(channel: JsAny, index: Int, value: Double): Unit = js("{ channel[index] = value; }")

private fun playBuffer(audio: JsAny, buffer: JsAny): Unit =
    js("{ const source = audio.createBufferSource(); source.buffer = buffer; source.connect(audio.destination); source.start(); }")
