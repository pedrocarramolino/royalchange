package com.royalchance.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.royalchance.core.common.text.formatGrouped
import com.royalchance.core.designsystem.component.ChipAmount
import com.royalchance.core.designsystem.motion.LocalReducedMotion
import com.royalchance.core.ui.resources.Res
import com.royalchance.core.ui.resources.chips_amount
import com.royalchance.domain.economy.Chips
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

private const val COUNT_MILLIS = 700

/** Fichas con separador de miles: "25.450". */
fun Chips.formatted(): String = formatGrouped(amount)

/** Texto completo de una cantidad, para frases y lectores de pantalla: "25.450 fichas". */
@Composable
fun chipsText(chips: Chips): String = stringResource(Res.string.chips_amount, chips.formatted())

/**
 * Cantidad de fichas con su icono. Cuando cambia, la cifra cuenta hasta el valor nuevo (salvo con
 * animaciones reducidas); los lectores de pantalla reciben siempre el valor final.
 */
@Composable
fun ChipBalance(
    chips: Chips,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.primary,
    glyphSize: Dp = 20.dp,
) {
    val reduced = LocalReducedMotion.current
    val shown = remember { Animatable(chips.amount.toFloat()) }
    var counting by remember { mutableStateOf(false) }
    LaunchedEffect(chips.amount, reduced) {
        if (reduced) {
            shown.snapTo(chips.amount.toFloat())
        } else {
            counting = true
            shown.animateTo(chips.amount.toFloat(), tween(COUNT_MILLIS, easing = FastOutSlowInEasing))
        }
        counting = false
    }
    val amount = if (counting) formatGrouped(shown.value.roundToLong()) else chips.formatted()
    ChipAmount(
        amount = amount,
        description = chipsText(chips),
        modifier = modifier,
        style = style,
        color = color,
        glyphSize = glyphSize,
    )
}
