package com.royalchance.feature.dice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.engine.dice.DiceRoll
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Caras que se ven mientras ruedan: una secuencia fija, no aleatoria (la animación nunca decide). */
private val TUMBLE = listOf(3, 6, 2, 5, 1, 4, 6, 3, 5, 2, 4, 1)

/**
 * Los dos dados. Con una tirada nueva ([throwId]) y [rolling], ruedan durante [durationMillis]
 * (caras cambiando, giro y botes que se amortiguan) y se paran en [roll]; después se avisa con
 * [onRollShown]. Decorativos: la pantalla anuncia el resultado.
 */
@Composable
internal fun DicePair(
    roll: DiceRoll?,
    throwId: Long?,
    rolling: Boolean,
    durationMillis: Int,
    onRollShown: (Long) -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(throwId) {
        if (throwId == null) return@LaunchedEffect
        if (rolling) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
        } else {
            progress.snapTo(1f)
        }
        onRollShown(throwId)
    }

    val p = progress.value
    val settled = p >= 1f
    val step = (p * 18).toInt()
    val damping = 1f - p
    Row(horizontalArrangement = Arrangement.spacedBy(size * 0.35f), modifier = modifier) {
        listOf(roll?.first ?: 5, roll?.second ?: 2).forEachIndexed { index, value ->
            val face = if (settled) value else TUMBLE[(step + index * 5) % TUMBLE.size]
            val phase = p * 6f * PI.toFloat() + index
            Die(
                face = face,
                size = size,
                modifier = Modifier
                    .offset(y = -(size * 0.35f) * abs(sin(phase)) * damping)
                    .rotate(if (settled) 0f else sin(phase) * 35f * damping + (index * 2 - 1) * 6f * damping),
            )
        }
    }
}

/** Un dado de marfil con sus puntos. */
@Composable
internal fun Die(face: Int, size: Dp, modifier: Modifier = Modifier) {
    val casino = RoyalTheme.casinoColors
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(Color(0xFFF7F3EA), cornerRadius = CornerRadius(w * 0.18f))
        drawRoundRect(casino.goldDeep, cornerRadius = CornerRadius(w * 0.18f), style = Stroke(width = w * 0.03f))
        val pip = w * 0.09f
        val a = w * 0.25f
        val b = w * 0.5f
        val c = w * 0.75f
        val pips = when (face) {
            1 -> listOf(b to b)
            2 -> listOf(a to a, c to c)
            3 -> listOf(a to a, b to b, c to c)
            4 -> listOf(a to a, c to a, a to c, c to c)
            5 -> listOf(a to a, c to a, b to b, a to c, c to c)
            else -> listOf(a to a, c to a, a to b, c to b, a to c, c to c)
        }
        val color = if (face == 1) casino.ruby else Color(0xFF1B1C22)
        pips.forEach { (x, y) -> drawCircle(color, pip, Offset(x, y)) }
    }
}
