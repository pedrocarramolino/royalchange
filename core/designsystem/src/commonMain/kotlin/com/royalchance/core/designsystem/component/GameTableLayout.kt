package com.royalchance.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pantalla de una mesa de juego: barra superior, mesa y controles.
 *
 * - En vertical (o con altura de sobra) los controles van debajo de la mesa.
 * - En horizontal con poca altura (un móvil girado) la mesa ocupa la izquierda y los controles
 *   pasan a un panel lateral con su propio desplazamiento: debajo dejarían la mesa en una franja.
 */
@Composable
fun GameTableLayout(
    topBar: @Composable () -> Unit,
    /** `null` si la pantalla aún no tiene controles (p. ej. antes de sentarse): la mesa ocupa todo. */
    controls: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    table: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val sidePanel = controls != null && isLandscapePhone(maxWidth, maxHeight)
        val panelWidth = (maxWidth * 0.4f).coerceIn(260.dp, 360.dp)
        Column(Modifier.fillMaxSize()) {
            topBar()
            if (sidePanel) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.weight(1f).fillMaxHeight(), content = table)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.width(panelWidth).fillMaxHeight(),
                    ) {
                        Column(Modifier.verticalScroll(rememberScrollState())) { controls?.invoke() }
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), content = table)
                controls?.invoke()
            }
        }
    }
}

/** Más ancho que alto y con poca altura: un móvil en horizontal. */
fun isLandscapePhone(width: Dp, height: Dp): Boolean = width > height && height < 600.dp
