package com.royalchance.feature.roulette

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.engine.roulette.PocketColor
import com.royalchance.engine.roulette.RouletteWheel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private const val SWEEP = 360f / RouletteWheel.POCKETS

/** Vueltas completas de la rueda y de la bola en cada giro. */
private const val WHEEL_TURNS = 4
private const val BALL_TURNS = 7

/** Frenada suave: arranca rápido y se detiene poco a poco, como una rueda real. */
private val SpinEasing = CubicBezierEasing(0.12f, 0.55f, 0.2f, 1f)

/**
 * Rueda europea dibujada. Con [number] la casilla queda bajo el marcador superior con la bola dentro;
 * cuando cambia [spinId] y [spinning] es `true`, la rueda gira y la bola cae en esa casilla al
 * final de [durationMillis] y avisa con [onSpinShown]. Decorativa: quien la use anuncia el resultado.
 */
@Composable
internal fun RouletteWheelView(
    number: Int?,
    spinId: Long?,
    spinning: Boolean,
    durationMillis: Int,
    onSpinShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val casino = RoyalTheme.casinoColors
    val measurer = rememberTextMeasurer()
    val wheelAngle = remember { Animatable(number?.let(::restingAngle) ?: 0f) }
    // Progreso de la bola: 0 = lanzada en el aro exterior, 1 = dentro de la casilla.
    val ball = remember { Animatable(if (number != null) 1f else -1f) }

    LaunchedEffect(spinId) {
        if (number == null) return@LaunchedEffect
        val target = restingAngle(number)
        if (!spinning) {
            wheelAngle.snapTo(target)
            ball.snapTo(1f)
            return@LaunchedEffect
        }
        val current = wheelAngle.value
        val delta = ((target - current) % 360f + 360f) % 360f
        ball.snapTo(0f)
        coroutineScope {
            val wheel = async { wheelAngle.animateTo(current + WHEEL_TURNS * 360f + delta, tween(durationMillis, easing = SpinEasing)) }
            ball.animateTo(1f, tween(durationMillis, easing = LinearEasing))
            wheel.await()
        }
        spinId?.let(onSpinShown)
    }

    val red = Color(0xFFB3263A)
    val black = Color(0xFF15171D)
    val green = Color(0xFF0E7A4F)
    val labelStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold)

    // Lo que solo depende del tamaño (las 37 etiquetas medidas) se calcula una vez; en cada
    // fotograma del giro solo se dibuja.
    Spacer(modifier.drawWithCache {
        val radius = size.minDimension / 2f
        val pocketOuter = radius * 0.86f
        val pocketInner = radius * 0.6f
        val ballTrack = radius * 0.93f
        val ballRest = radius * 0.66f
        val labelFont = labelStyle.copy(fontSize = (radius * 0.085f).toSp())
        val labels = RouletteWheel.ORDER.map { measurer.measure(it.toString(), labelFont) }
        val center = Offset(size.width / 2f, size.height / 2f)
        val marker = Path().apply {
            moveTo(center.x - radius * 0.05f, center.y - radius)
            lineTo(center.x + radius * 0.05f, center.y - radius)
            lineTo(center.x, center.y - radius * 0.88f)
            close()
        }
        onDrawBehind {

        // Aro exterior de madera y oro.
        drawCircle(Color(0xFF3A2414), radius)
        drawCircle(casino.goldBrush, radius * 0.97f, style = Stroke(width = radius * 0.03f))

        rotate(wheelAngle.value) {
            val arcSize = Size(pocketOuter * 2f, pocketOuter * 2f)
            val topLeft = Offset(center.x - pocketOuter, center.y - pocketOuter)
            RouletteWheel.ORDER.forEachIndexed { index, pocket ->
                val color = when (RouletteWheel.colorOf(pocket)) {
                    PocketColor.Green -> green
                    PocketColor.Red -> red
                    PocketColor.Black -> black
                }
                drawArc(color, startAngle = -90f - SWEEP / 2f + index * SWEEP, sweepAngle = SWEEP, useCenter = true, topLeft = topLeft, size = arcSize)
            }
            // Separadores dorados entre casillas.
            RouletteWheel.ORDER.indices.forEach { index ->
                val angle = (-90f - SWEEP / 2f + index * SWEEP) * PI.toFloat() / 180f
                drawLine(
                    casino.gold.copy(alpha = 0.7f),
                    start = center + Offset(cos(angle), sin(angle)) * pocketInner,
                    end = center + Offset(cos(angle), sin(angle)) * pocketOuter,
                    strokeWidth = radius * 0.008f,
                )
            }
            // Números, en la parte exterior de cada casilla.
            labels.forEachIndexed { index, layout ->
                rotate(index * SWEEP, pivot = center) {
                    drawText(
                        layout,
                        topLeft = Offset(center.x - layout.size.width / 2f, center.y - pocketOuter + radius * 0.03f),
                    )
                }
            }
            // Cono central con la cruz dorada.
            drawCircle(casino.feltShadow, pocketInner)
            drawCircle(casino.goldBrush, pocketInner, style = Stroke(width = radius * 0.02f))
            drawCircle(Color(0xFF2A1A0E), radius * 0.36f)
            repeat(4) { arm ->
                val angle = arm * PI.toFloat() / 2f
                drawLine(
                    casino.goldBrush,
                    start = center,
                    end = center + Offset(cos(angle), sin(angle)) * (radius * 0.3f),
                    strokeWidth = radius * 0.035f,
                )
            }
            drawCircle(casino.goldBrush, radius * 0.08f)
        }

        // Marcador fijo arriba.
        drawPath(marker, casino.goldBrush)

        // Bola: gira al revés por el aro exterior, pierde velocidad y cae en la casilla de arriba.
        val progress = ball.value
        if (progress >= 0f) {
            val remaining = (1f - progress).pow(2.2f)
            val angleDegrees = -90f - remaining * BALL_TURNS * 360f
            val fall = ((progress - 0.72f) / 0.28f).coerceIn(0f, 1f)
            val distance = ballTrack + (ballRest - ballTrack) * fall
            val angle = angleDegrees * PI.toFloat() / 180f
            val position = center + Offset(cos(angle), sin(angle)) * distance
            drawCircle(Color.Black.copy(alpha = 0.35f), radius * 0.042f, position + Offset(radius * 0.01f, radius * 0.012f))
            drawCircle(Color(0xFFF4F1EA), radius * 0.04f, position)
        }
        }
    })
}

/** Ángulo de la rueda con [number] bajo el marcador superior. */
private fun restingAngle(number: Int): Float = -RouletteWheel.indexOf(number) * SWEEP
