package com.royalchance.feature.auth.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.adaptive.LocalWindowWidthClass
import com.royalchance.core.designsystem.adaptive.WindowWidthClass
import com.royalchance.core.designsystem.component.BrandLockup
import com.royalchance.core.designsystem.component.RoyalTopBar
import com.royalchance.core.designsystem.component.feltBackground
import com.royalchance.core.designsystem.theme.RoyalSizes
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.feature.auth.resources.Res
import com.royalchance.feature.auth.resources.auth_back
import com.royalchance.feature.auth.resources.auth_tagline
import org.jetbrains.compose.resources.stringResource

/**
 * Estructura común de las pantallas de acceso.
 * - Móvil y tablet: columna centrada y desplazable, con barra superior si hay [onBack].
 * - Escritorio: panel de tapete con la marca a la izquierda y el formulario a la derecha.
 *
 * Aplica los márgenes del sistema (incluido el teclado en pantalla), así que el contenido
 * nunca queda tapado.
 */
@Composable
internal fun AuthScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showBrandOnCompact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expanded = LocalWindowWidthClass.current == WindowWidthClass.Expanded
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (expanded) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .feltBackground(RoyalTheme.casinoColors.feltBrush),
                ) {
                    BrandLockup(
                        emblemSize = 140.dp,
                        tagline = stringResource(Res.string.auth_tagline),
                        onFelt = true,
                        modifier = Modifier.padding(RoyalSpacing.xxl),
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                if (onBack != null) {
                    RoyalTopBar(
                        title = "",
                        onBack = onBack,
                        backDescription = stringResource(Res.string.auth_back),
                        windowInsets = WindowInsets(0),
                    )
                }
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(RoyalSpacing.l),
                        modifier = Modifier
                            .widthIn(max = RoyalSizes.formMaxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = RoyalSpacing.xl, vertical = RoyalSpacing.l),
                    ) {
                        if (showBrandOnCompact && !expanded) {
                            BrandLockup(
                                emblemSize = 104.dp,
                                tagline = stringResource(Res.string.auth_tagline),
                                modifier = Modifier.fillMaxWidth().padding(top = RoyalSpacing.xl, bottom = RoyalSpacing.l),
                            )
                        }
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (title != null || subtitle != null) Spacer(Modifier.height(RoyalSpacing.xs))
                        content()
                        Spacer(Modifier.height(RoyalSpacing.xl))
                    }
                }
            }
        }
    }
}
