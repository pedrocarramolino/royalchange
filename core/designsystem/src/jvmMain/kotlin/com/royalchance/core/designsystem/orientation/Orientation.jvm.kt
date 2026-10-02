package com.royalchance.core.designsystem.orientation

import androidx.compose.runtime.Composable

/** Escritorio: la ventana no tiene orientación que fijar. */
@Composable
actual fun rememberOrientationLock(): (ScreenOrientation) -> Unit = {}
