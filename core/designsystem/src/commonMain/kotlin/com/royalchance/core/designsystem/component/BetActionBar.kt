package com.royalchance.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.theme.RoyalSpacing

/** Una acción de texto de la barra de apuestas (deshacer, borrar, repetir). */
class BetBarAction(val text: String, val enabled: Boolean, val onClick: () -> Unit)

/**
 * Barra de apuestas en una sola fila, para mesas de fichas (ruleta, dados) en un móvil girado:
 * fichas, acciones de texto y el botón principal (girar, tirar). [notice] va encima si lo hay.
 */
@Composable
fun BetActionBar(
    chipValues: List<Long>,
    selectedChip: Long,
    chipDescription: @Composable (Long) -> String,
    onSelectChip: (Long) -> Unit,
    actions: List<BetBarAction>,
    primaryText: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean,
    primaryLoading: Boolean,
    modifier: Modifier = Modifier,
    notice: (@Composable () -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = RoyalSpacing.l, vertical = RoyalSpacing.xs)) {
            notice?.invoke()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chipValues.forEach { value ->
                    BetChip(
                        value = value,
                        description = chipDescription(value),
                        onClick = { onSelectChip(value) },
                        selected = value == selectedChip,
                        size = 38.dp,
                    )
                }
                Spacer(Modifier.size(RoyalSpacing.xs))
                actions.forEach { action ->
                    RoyalTextButton(text = action.text, onClick = action.onClick, enabled = action.enabled, modifier = Modifier.weight(1f))
                }
                RoyalPrimaryButton(
                    text = primaryText,
                    onClick = onPrimary,
                    enabled = primaryEnabled,
                    loading = primaryLoading,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}
