package com.royalchance.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.theme.RoyalTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animaciones reducidas (preferencia del jugador): sin celebraciones ni entradas decorativas. Las
 * animaciones que cuentan el resultado (la rueda, los rodillos, los dados) se mantienen.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Entrada de una carta repartida: cae desde arriba, gira un poco y aparece. */
fun Modifier.dealIn(): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    val distance = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!reduced) progress.animateTo(1f, tween(DEAL_MILLIS, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = -distance * (1f - p)
        rotationZ = -8f * (1f - p)
    }
}

/**
 * Lluvia de monedas para un premio grande. Cada valor nuevo de [trigger] la lanza una vez; `null`
 * no hace nada. Decorativa y sin azar: las trayectorias salen del índice de cada moneda.
 */
@Composable
fun WinCelebration(trigger: Long?, modifier: Modifier = Modifier) {
    if (LocalReducedMotion.current) return
    val casino = RoyalTheme.casinoColors
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(CELEBRATION_MILLIS, easing = LinearEasing))
    }
    val p = progress.value
    if (p >= 1f) return
    Canvas(modifier) {
        val origin = Offset(size.width / 2f, size.height * 0.45f)
        val reach = size.minDimension * 0.55f
        val gravity = size.height * 0.9f
        val coinRadius = 9.dp.toPx()
        repeat(COINS) { index ->
            // Ángulo áureo: reparto uniforme sin aleatoriedad.
            val angle = index * GOLDEN_ANGLE
            val speed = 0.55f + (index % 7) / 10f
            val x = origin.x + cos(angle).toFloat() * reach * speed * p
            val y = origin.y + sin(angle).toFloat() * reach * speed * p - reach * 0.6f * p + gravity * p * p
            val alpha = (1f - p).coerceIn(0f, 1f)
            // Giro de la moneda: se estrecha y se ensancha.
            val spin = cos((p * 6 + index) * PI).toFloat()
            scale(scaleX = 0.25f + 0.75f * abs(spin), scaleY = 1f, pivot = Offset(x, y)) {
                drawCircle(casino.goldBrush, coinRadius, Offset(x, y), alpha = alpha)
                drawCircle(Color.White.copy(alpha = 0.45f * alpha), coinRadius * 0.35f, Offset(x - coinRadius * 0.3f, y - coinRadius * 0.3f))
            }
        }
    }
}

private const val DEAL_MILLIS = 280
private const val CELEBRATION_MILLIS = 1_800
private const val COINS = 36
private const val GOLDEN_ANGLE = 2.399963
