package com.royalchance.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.royalchance.core.common.text.formatGrouped
import com.royalchance.core.designsystem.component.ChipAmount
import com.royalchance.core.ui.resources.Res
import com.royalchance.core.ui.resources.chips_amount
import com.royalchance.domain.economy.Chips
import org.jetbrains.compose.resources.stringResource

/** Fichas con separador de miles: "25.450". */
fun Chips.formatted(): String = formatGrouped(amount)

/** Texto completo de una cantidad, para frases y lectores de pantalla: "25.450 fichas". */
@Composable
fun chipsText(chips: Chips): String = stringResource(Res.string.chips_amount, chips.formatted())

/** Cantidad de fichas con su icono. */
@Composable
fun ChipBalance(
    chips: Chips,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.primary,
    glyphSize: Dp = 20.dp,
) {
    ChipAmount(
        amount = chips.formatted(),
        description = chipsText(chips),
        modifier = modifier,
        style = style,
        color = color,
        glyphSize = glyphSize,
    )
}
