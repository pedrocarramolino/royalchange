@file:OptIn(ExperimentalWasmJsInterop::class)

package com.royalchance.core.designsystem.orientation

import androidx.compose.runtime.Composable

/**
 * Web: intenta fijar la orientación con la API de orientación de pantalla. Los navegadores solo lo
 * permiten a pantalla completa o en la app instalada (y Safari en iOS, nunca): si no se puede, el
 * aviso de girar el móvil hace el resto.
 */
@Composable
actual fun rememberOrientationLock(): (ScreenOrientation) -> Unit = { orientation ->
    lockOrientation(if (orientation == ScreenOrientation.Landscape) "landscape" else "portrait")
}

private fun lockOrientation(orientation: String): Unit =
    js("{ try { if (screen.orientation && screen.orientation.lock) { screen.orientation.lock(orientation).catch(function () {}); } } catch (e) {} }")
