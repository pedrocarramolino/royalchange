package com.royalchance.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.adaptive.LocalWindowWidthClass
import com.royalchance.core.designsystem.adaptive.WindowWidthClass
import com.royalchance.core.designsystem.theme.RoyalSpacing

/** Destino de la navegación principal. */
data class NavigationItem<K>(val key: K, val label: String, val icon: ImageVector)

/**
 * Navegación principal que se adapta al ancho disponible:
 * - compacto (móvil): barra inferior, al alcance del pulgar;
 * - medio (tablet): rail lateral;
 * - expandido (escritorio): panel lateral permanente con cabecera.
 *
 * Gestiona los márgenes del sistema (barras, muescas): el [content] no debe volver a aplicarlos.
 * Con [navigationVisible] a `false` (pantallas de detalle, juegos inmersivos) muestra solo el
 * contenido, que pasa a ser responsable de sus propios márgenes.
 */
@Composable
fun <K> AdaptiveNavigationScaffold(
    items: List<NavigationItem<K>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    navigationVisible: Boolean = true,
    drawerHeader: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // El contenido cambia de sitio en el árbol al mostrar/ocultar la navegación o al redimensionar:
    // movableContentOf lo traslada conservando su estado (pila, scroll, ViewModels).
    val currentContent by rememberUpdatedState(content)
    val movableContent = remember { movableContentOf { currentContent() } }

    if (!navigationVisible) {
        Box(modifier.fillMaxSize()) { movableContent() }
        return
    }
    when (LocalWindowWidthClass.current) {
        WindowWidthClass.Compact -> Scaffold(
            modifier = modifier,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = item.key == selected,
                            onClick = { onSelect(item.key) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { movableContent() }
        }

        WindowWidthClass.Medium -> Row(modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                header = {
                    BrandEmblem(size = 48.dp, modifier = Modifier.padding(vertical = RoyalSpacing.m))
                },
            ) {
                Spacer(Modifier.weight(1f))
                items.forEach { item ->
                    NavigationRailItem(
                        selected = item.key == selected,
                        onClick = { onSelect(item.key) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            ContentPane(movableContent)
        }

        WindowWidthClass.Expanded -> Row(modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.width(280.dp).fillMaxHeight(),
            ) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                        .padding(RoyalSpacing.m)
                        .selectableGroup(),
                ) {
                    drawerHeader()
                    Spacer(Modifier.height(RoyalSpacing.l))
                    items.forEach { item ->
                        NavigationDrawerItem(
                            selected = item.key == selected,
                            onClick = { onSelect(item.key) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label, style = MaterialTheme.typography.titleMedium) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            modifier = Modifier.padding(vertical = RoyalSpacing.xxs),
                        )
                    }
                }
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ContentPane(movableContent)
        }
    }
}

@Composable
private fun ContentPane(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)),
    ) {
        content()
    }
}
