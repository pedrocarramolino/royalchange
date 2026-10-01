package com.royalchance.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.theme.RoyalTheme

/**
 * Barra de avance (experiencia, logros). [description] es lo que oye el lector de pantalla
 * ("1.230 de 1.800 puntos de experiencia"), además del porcentaje.
 */
@Composable
fun RoyalProgressBar(
    fraction: Float,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = RoyalTheme.casinoColors.gold,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val value = fraction.coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { value },
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clearAndSetSemantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
            },
    )
}
