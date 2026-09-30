package com.royalchance.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.resources.Res
import com.royalchance.core.designsystem.resources.ds_requirement_met
import com.royalchance.core.designsystem.resources.ds_requirement_pending
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import org.jetbrains.compose.resources.stringResource

enum class BannerTone { Info, Success, Warning, Error }

/**
 * Aviso destacado dentro del contenido (no bloquea la pantalla). Los lectores de pantalla lo
 * anuncian al aparecer.
 */
@Composable
fun InfoBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    icon: ImageVector = RoyalIcons.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        BannerTone.Info -> colors.surfaceContainerHigh to colors.onSurface
        BannerTone.Success -> colors.secondaryContainer to colors.onSecondaryContainer
        BannerTone.Warning -> colors.primaryContainer to colors.onPrimaryContainer
        BannerTone.Error -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = RoyalSpacing.l, end = RoyalSpacing.s, top = RoyalSpacing.m, bottom = RoyalSpacing.m),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(RoyalSpacing.m))
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                RoyalTextButton(text = actionLabel, onClick = onAction, color = content)
            }
        }
    }
}

/** Estado vacío: explica qué aparecerá aquí y, si procede, ofrece la acción para empezar. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(RoyalSpacing.xl), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.m),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(88.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(RoyalSpacing.s))
                action()
            }
        }
    }
}

/** Título de sección dentro de una pantalla. Marcado como encabezado para lectores de pantalla. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = RoyalSpacing.s, bottom = RoyalSpacing.xs).semantics { heading() },
    )
}

/** Indicador segmentado (p. ej. fortaleza de contraseña). */
@Composable
fun SegmentedMeter(
    filledSegments: Int,
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    totalSegments: Int = 4,
) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.xs), modifier = Modifier.fillMaxWidth()) {
            repeat(totalSegments) { index ->
                Surface(
                    color = if (index < filledSegments) color else MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.weight(1f).height(4.dp),
                ) {}
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(top = RoyalSpacing.xs),
        )
    }
}

/** Elemento de una lista de requisitos, con marca de cumplido o pendiente. */
@Composable
fun ChecklistItem(text: String, met: Boolean, modifier: Modifier = Modifier) {
    val color = if (met) RoyalTheme.casinoColors.success else MaterialTheme.colorScheme.onSurfaceVariant
    val state = stringResource(if (met) Res.string.ds_requirement_met else Res.string.ds_requirement_pending)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = "$text, $state" },
    ) {
        Icon(
            imageVector = if (met) RoyalIcons.Check else RoyalIcons.Close,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(RoyalSpacing.s))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
