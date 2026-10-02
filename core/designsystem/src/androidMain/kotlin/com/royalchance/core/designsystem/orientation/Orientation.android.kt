package com.royalchance.core.designsystem.orientation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** Android: fija la orientación de la actividad. En tablets (lado corto ≥ 600 dp) no la fija. */
@Composable
actual fun rememberOrientationLock(): (ScreenOrientation) -> Unit {
    val context = LocalContext.current
    val tablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    return remember(context, tablet) {
        val activity = context.findActivity()
        val lock: (ScreenOrientation) -> Unit = { orientation ->
            if (!tablet) {
                activity?.requestedOrientation = when (orientation) {
                    ScreenOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    ScreenOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
        }
        lock
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
