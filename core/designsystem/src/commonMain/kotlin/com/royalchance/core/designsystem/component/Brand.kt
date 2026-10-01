package com.royalchance.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royalchance.core.designsystem.graphics.SuitGlyph
import com.royalchance.core.designsystem.graphics.SuitShape
import com.royalchance.core.designsystem.resources.Res
import com.royalchance.core.designsystem.resources.ds_brand_name
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import org.jetbrains.compose.resources.stringResource

/** Emblema de la marca: pica dorada dentro de un anillo, sobre tapete. */
@Composable
fun BrandEmblem(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val casino = RoyalTheme.casinoColors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .drawBehind {
                drawCircle(brush = casino.feltBrush)
                drawCircle(
                    brush = casino.goldBrush,
                    radius = this.size.minDimension / 2f - 3.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx()),
                    alpha = 0.7f,
                )
            },
    ) {
        SuitGlyph(SuitShape.Spades, brush = casino.goldBrush, modifier = Modifier.size(size * 0.52f))
    }
}

/** Logotipo tipográfico "ROYAL CHANCE" con espaciado amplio. */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    letterSpacing: TextUnit = 4.sp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val name = stringResource(Res.string.ds_brand_name)
    Text(
        text = name.uppercase(),
        style = style.copy(letterSpacing = letterSpacing),
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier.semantics {
            heading()
            contentDescription = name
        },
    )
}

/**
 * Emblema y nombre centrados, para pantallas de bienvenida y cabeceras.
 * [onFelt] usa colores fijos pensados para el tapete (mismo contraste en tema claro y oscuro).
 */
@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    emblemSize: Dp = 96.dp,
    tagline: String? = null,
    onFelt: Boolean = false,
) {
    val casino = RoyalTheme.casinoColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RoyalSpacing.l),
        modifier = modifier,
    ) {
        BrandEmblem(size = emblemSize)
        BrandWordmark(color = if (onFelt) casino.gold else MaterialTheme.colorScheme.primary)
        if (tagline != null) {
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodyLarge,
                color = if (onFelt) casino.onFelt.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Fondo de tapete con viñeta y un leve brillo central. Para paneles decorativos. */
fun Modifier.feltBackground(brush: Brush): Modifier = drawBehind {
    drawRect(brush)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.35f),
            radius = size.maxDimension * 0.6f,
        ),
        radius = size.maxDimension,
        center = Offset(size.width / 2f, size.height * 0.35f),
    )
}

/** Pantalla de espera a pantalla completa (arranque, restauración de sesión). */
@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BrandEmblem(size = 88.dp)
            Box(Modifier.size(RoyalSpacing.xl))
            LoadingSpinner(color = MaterialTheme.colorScheme.primary)
        }
    }
}
