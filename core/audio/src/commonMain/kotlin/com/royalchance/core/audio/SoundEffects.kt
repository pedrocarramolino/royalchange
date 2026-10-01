package com.royalchance.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Suena [sound] cada vez que [key] cambia a un valor no nulo. No suena al entrar en la pantalla:
 * volver a una mesa que ya muestra un resultado no repite su sonido.
 */
@Composable
fun SoundOnChange(key: Any?, sound: Sound?) {
    val player = rememberSoundPlayer()
    var last by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key != null && key != last && sound != null) player.play(sound)
        last = key
    }
}

/** Suena [sound] cada vez que [count] crece (cartas repartidas, fichas en el bote…). */
@Composable
fun SoundOnIncrease(count: Int, sound: Sound) {
    val player = rememberSoundPlayer()
    var last by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > last) player.play(sound)
        last = count
    }
}

/** Un premio es "grande" (fanfarria y lluvia de monedas) desde este múltiplo de lo apostado. */
const val BIG_WIN_MULTIPLIER: Long = 10

/** Sonido del resultado de una ronda según lo ganado o perdido. */
fun resultSound(net: Long, bigWin: Boolean): Sound? = when {
    bigWin -> Sound.BigWin
    net > 0 -> Sound.Win
    net < 0 -> Sound.Lose
    else -> null
}
