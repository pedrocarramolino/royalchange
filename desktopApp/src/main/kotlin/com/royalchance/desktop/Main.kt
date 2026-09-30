package com.royalchance.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.royalchance.shared.App
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Royal Chance",
        state = rememberWindowState(size = DpSize(1280.dp, 800.dp)),
    ) {
        // Mínimo de tamaño móvil: permite probar los layouts compacto, medio y expandido redimensionando.
        LaunchedEffect(Unit) { window.minimumSize = Dimension(360, 640) }
        App()
    }
}
