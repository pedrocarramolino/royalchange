package com.royalchance.feature.lobby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royalchance.core.designsystem.graphics.SuitGlyph
import com.royalchance.core.designsystem.graphics.SuitShape
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.domain.game.GameType

/** Ilustración vectorial de cada juego para su tarjeta del lobby. Decorativa: sin descripción accesible. */
@Composable
internal fun GameArt(game: GameType, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val casino = RoyalTheme.casinoColors
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when (game) {
            GameType.Blackjack -> SuitGlyph(SuitShape.Spades, casino.goldBrush, Modifier.size(size * 0.85f))
            GameType.Poker -> SuitGlyph(SuitShape.Hearts, SolidColor(casino.suitRed), Modifier.size(size * 0.8f))
            GameType.Roulette -> RouletteWheelArt(Modifier.size(size))
            GameType.Slots -> Text(
                text = "777",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = (size.value * 0.36f).sp),
                color = casino.gold,
            )
            GameType.Dice -> Icon(RoyalIcons.Dice, contentDescription = null, tint = casino.ivory, modifier = Modifier.size(size * 0.8f))
        }
    }
}

/** Rueda de ruleta estilizada: casillas alternas rojo/negro, el cero en verde y aro dorado. */
@Composable
private fun RouletteWheelArt(modifier: Modifier) {
    val casino = RoyalTheme.casinoColors
    Canvas(modifier) {
        val pockets = 12
        val sweep = 360f / pockets
        val radius = size.minDimension / 2f
        val inset = radius * 0.08f
        val arcSize = Size((radius - inset) * 2f, (radius - inset) * 2f)
        val topLeft = Offset(center.x - radius + inset, center.y - radius + inset)
        repeat(pockets) { index ->
            val color = when {
                index == 0 -> casino.felt
                index % 2 == 0 -> casino.suitRed
                else -> Color(0xFF15171D)
            }
            drawArc(color, startAngle = -90f - sweep / 2 + index * sweep, sweepAngle = sweep, useCenter = true, topLeft = topLeft, size = arcSize)
        }
        drawCircle(casino.goldBrush, radius = radius - inset / 2f, style = Stroke(width = inset))
        drawCircle(casino.feltShadow, radius = radius * 0.38f)
        drawCircle(casino.goldBrush, radius = radius * 0.16f)
    }
}
