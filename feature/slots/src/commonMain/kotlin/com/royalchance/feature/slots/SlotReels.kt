package com.royalchance.feature.slots

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royalchance.core.designsystem.graphics.SuitGlyph
import com.royalchance.core.designsystem.graphics.SuitShape
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.engine.slots.SlotMachine
import com.royalchance.engine.slots.SlotSymbol
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.floor

/** Vueltas completas de la tira del primer rodillo; cada rodillo siguiente da una más. */
private const val BASE_TURNS = 2

/** Frenada de cada rodillo, con un pequeño rebote final. */
private val ReelEasing = CubicBezierEasing(0.2f, 0.6f, 0.35f, 1.08f)

/**
 * Los cinco rodillos. Con un giro nuevo ([spinId]) y [spinning], cada rodillo gira y se para en
 * su posición, uno tras otro; el último termina a los [durationMillis] y se avisa con [onSpinShown].
 * [highlighted] marca las celdas premiadas (`rodillo` a `fila`). Decorativo: la pantalla anuncia
 * el resultado.
 */
@Composable
internal fun SlotReels(
    stops: List<Int>?,
    spinId: Long?,
    spinning: Boolean,
    durationMillis: Int,
    highlighted: Set<Pair<Int, Int>>,
    onSpinShown: (Long) -> Unit,
    cellSize: Dp,
    modifier: Modifier = Modifier,
) {
    val casino = RoyalTheme.casinoColors
    val positions = remember { List(SlotMachine.REELS) { reel -> Animatable(stops?.get(reel)?.toFloat() ?: (reel * 7f)) } }

    LaunchedEffect(spinId) {
        if (stops == null || spinId == null) return@LaunchedEffect
        if (!spinning) {
            positions.forEachIndexed { reel, position -> position.snapTo(stops[reel].toFloat()) }
            return@LaunchedEffect
        }
        coroutineScope {
            positions.mapIndexed { reel, position ->
                async {
                    val size = SlotMachine.STRIPS[reel].size
                    val current = position.value
                    // Los símbolos bajan: la posición decrece hasta la parada.
                    val delta = (((current - stops[reel]) % size) + size) % size
                    val target = current - (BASE_TURNS + reel) * size - delta
                    val duration = (durationMillis * (0.55f + 0.1125f * reel)).toInt()
                    position.animateTo(target, tween(duration, easing = ReelEasing))
                    position.snapTo(stops[reel].toFloat())
                }
            }.awaitAll()
        }
        onSpinShown(spinId)
    }

    Row(
        modifier
            .background(Color(0xFF0B0C10), RoundedCornerShape(16.dp))
            .border(BorderStroke(3.dp, casino.goldBrush), RoundedCornerShape(16.dp))
            .padding(8.dp),
    ) {
        positions.forEachIndexed { reel, position ->
            Reel(reel, position.value, highlighted, cellSize)
        }
    }
}

@Composable
private fun Reel(reel: Int, position: Float, highlighted: Set<Pair<Int, Int>>, cellSize: Dp) {
    val strip = SlotMachine.STRIPS[reel]
    val top = floor(position).toInt()
    val fraction = position - top
    Box(
        Modifier
            .size(cellSize, cellSize * SlotMachine.ROWS)
            .clipToBounds()
            .padding(horizontal = 2.dp)
            .background(Color(0xFF16181F), RoundedCornerShape(8.dp)),
    ) {
        // Una celda más por encima y por debajo para que el desplazamiento no deje huecos.
        for (k in -1..SlotMachine.ROWS) {
            val index = (top + k).mod(strip.size)
            val row = k.takeIf { fraction == 0f && it in 0 until SlotMachine.ROWS }
            SymbolCell(
                symbol = strip[index],
                highlighted = row != null && (reel to row) in highlighted,
                size = cellSize,
                modifier = Modifier.offset(y = cellSize * (k - fraction)),
            )
        }
    }
}

@Composable
private fun SymbolCell(symbol: SlotSymbol, highlighted: Boolean, size: Dp, modifier: Modifier) {
    val casino = RoyalTheme.casinoColors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .padding(3.dp)
            .then(if (highlighted) Modifier.border(2.dp, casino.gold, RoundedCornerShape(8.dp)).background(casino.gold.copy(alpha = 0.15f), RoundedCornerShape(8.dp)) else Modifier),
    ) {
        SlotSymbolArt(symbol, size * 0.68f)
    }
}

/** Dibujo de cada símbolo, sin imágenes ni emojis: vectorial y nítido a cualquier tamaño. */
@Composable
internal fun SlotSymbolArt(symbol: SlotSymbol, size: Dp) {
    val casino = RoyalTheme.casinoColors
    when (symbol) {
        SlotSymbol.Cherry -> Cherry(size)
        SlotSymbol.Club -> SuitGlyph(SuitShape.Clubs, SolidColor(Color(0xFF5CC79E)), Modifier.size(size))
        SlotSymbol.Heart -> SuitGlyph(SuitShape.Hearts, SolidColor(casino.suitRed), Modifier.size(size))
        SlotSymbol.Spade -> SuitGlyph(SuitShape.Spades, SolidColor(casino.ivory), Modifier.size(size))
        SlotSymbol.Diamond -> SuitGlyph(SuitShape.Diamonds, SolidColor(Color(0xFF59A8F0)), Modifier.size(size))
        SlotSymbol.Bar -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size, size * 0.55f).background(casino.goldBrush, RoundedCornerShape(6.dp)),
        ) {
            Text("BAR", color = casino.onGold, fontWeight = FontWeight.Black, fontSize = (size.value * 0.3f).sp)
        }
        SlotSymbol.Seven -> Text(
            "7",
            color = casino.suitRed,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.95f).sp,
            style = MaterialTheme.typography.displaySmall,
        )
        SlotSymbol.Wild -> Crown(size)
    }
}

@Composable
private fun Cherry(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stem = Color(0xFF4CAF50)
        drawLine(stem, Offset(w * 0.3f, w * 0.62f), Offset(w * 0.62f, w * 0.12f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
        drawLine(stem, Offset(w * 0.72f, w * 0.6f), Offset(w * 0.62f, w * 0.12f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
        drawCircle(Color(0xFFD7263D), w * 0.2f, Offset(w * 0.3f, w * 0.72f))
        drawCircle(Color(0xFFB3132A), w * 0.2f, Offset(w * 0.72f, w * 0.7f))
        drawCircle(Color.White.copy(alpha = 0.5f), w * 0.05f, Offset(w * 0.24f, w * 0.66f))
        drawCircle(Color.White.copy(alpha = 0.5f), w * 0.05f, Offset(w * 0.66f, w * 0.64f))
    }
}

@Composable
private fun Crown(size: Dp) {
    val casino = RoyalTheme.casinoColors
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val crown = Path().apply {
            moveTo(w * 0.1f, w * 0.78f)
            lineTo(w * 0.04f, w * 0.3f)
            lineTo(w * 0.3f, w * 0.52f)
            lineTo(w * 0.5f, w * 0.18f)
            lineTo(w * 0.7f, w * 0.52f)
            lineTo(w * 0.96f, w * 0.3f)
            lineTo(w * 0.9f, w * 0.78f)
            close()
        }
        drawPath(crown, casino.goldBrush)
        drawRect(casino.goldBrush, Offset(w * 0.1f, w * 0.8f), Size(w * 0.8f, w * 0.1f))
        listOf(0.04f to 0.3f, 0.5f to 0.18f, 0.96f to 0.3f).forEach { (x, y) ->
            drawCircle(casino.ruby, w * 0.06f, Offset(w * x, w * y))
        }
    }
}
