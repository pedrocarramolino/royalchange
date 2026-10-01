package com.royalchance.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.resources.Res
import com.royalchance.core.designsystem.resources.ds_loading
import com.royalchance.core.designsystem.theme.RoyalSizes
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Acción principal: dorada, alta y con texto grande. Una por pantalla.
 * Mientras [loading] es `true` muestra un indicador y no admite pulsaciones.
 */
@Composable
fun RoyalPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val casino = RoyalTheme.casinoColors
    val shape = MaterialTheme.shapes.medium
    val active = enabled && !loading
    Surface(
        onClick = onClick,
        enabled = active,
        shape = shape,
        color = Color.Transparent,
        contentColor = casino.onGold,
        modifier = modifier
            .heightIn(min = RoyalSizes.primaryActionHeight)
            .background(
                brush = if (enabled) casino.goldBrush else SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                shape = shape,
            )
            .semantics { if (loading) contentDescription = text },
    ) {
        ButtonContent(
            text = text,
            loading = loading,
            contentColor = if (enabled) casino.onGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

/** Acción secundaria: contorno dorado. */
@Composable
fun RoyalSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = RoyalSpacing.xl),
        modifier = modifier.heightIn(min = RoyalSizes.primaryActionHeight),
    ) {
        ButtonContent(text = text, loading = loading, contentColor = LocalContentColor.current)
    }
}

/** Acción terciaria o enlace. Mantiene el mínimo táctil de 48 dp. */
@Composable
fun RoyalTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        modifier = modifier.heightIn(min = RoyalSizes.minTouchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ButtonContent(text: String, loading: Boolean, contentColor: Color) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = RoyalSpacing.xl)) {
        if (loading) {
            LoadingSpinner(color = contentColor)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun LoadingSpinner(color: Color, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.ds_loading)
    CircularProgressIndicator(
        color = color,
        strokeWidth = 2.5.dp,
        modifier = modifier.size(22.dp).semantics { contentDescription = description },
    )
}
