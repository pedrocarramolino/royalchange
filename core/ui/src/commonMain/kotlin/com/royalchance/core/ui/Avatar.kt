package com.royalchance.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.graphics.SuitGlyph
import com.royalchance.core.designsystem.graphics.SuitShape
import com.royalchance.core.designsystem.theme.CasinoColors
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.ui.resources.Res
import com.royalchance.core.ui.resources.avatar_club_emerald
import com.royalchance.core.ui.resources.avatar_club_gold
import com.royalchance.core.ui.resources.avatar_diamond_gold
import com.royalchance.core.ui.resources.avatar_diamond_ruby
import com.royalchance.core.ui.resources.avatar_heart_gold
import com.royalchance.core.ui.resources.avatar_heart_ruby
import com.royalchance.core.ui.resources.avatar_picker_label
import com.royalchance.core.ui.resources.avatar_spade_gold
import com.royalchance.core.ui.resources.avatar_spade_ivory
import com.royalchance.domain.auth.AvatarId
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class AvatarFinish { Gold, Ruby, Ivory, Emerald }

private data class AvatarStyle(val suit: SuitShape, val finish: AvatarFinish, val name: StringResource)

private val AvatarId.style: AvatarStyle
    get() = when (this) {
        AvatarId.SpadeGold -> AvatarStyle(SuitShape.Spades, AvatarFinish.Gold, Res.string.avatar_spade_gold)
        AvatarId.HeartRuby -> AvatarStyle(SuitShape.Hearts, AvatarFinish.Ruby, Res.string.avatar_heart_ruby)
        AvatarId.DiamondGold -> AvatarStyle(SuitShape.Diamonds, AvatarFinish.Gold, Res.string.avatar_diamond_gold)
        AvatarId.ClubEmerald -> AvatarStyle(SuitShape.Clubs, AvatarFinish.Emerald, Res.string.avatar_club_emerald)
        AvatarId.SpadeIvory -> AvatarStyle(SuitShape.Spades, AvatarFinish.Ivory, Res.string.avatar_spade_ivory)
        AvatarId.HeartGold -> AvatarStyle(SuitShape.Hearts, AvatarFinish.Gold, Res.string.avatar_heart_gold)
        AvatarId.DiamondRuby -> AvatarStyle(SuitShape.Diamonds, AvatarFinish.Ruby, Res.string.avatar_diamond_ruby)
        AvatarId.ClubGold -> AvatarStyle(SuitShape.Clubs, AvatarFinish.Gold, Res.string.avatar_club_gold)
    }

private fun AvatarFinish.brush(colors: CasinoColors): Brush = when (this) {
    AvatarFinish.Gold -> colors.goldBrush
    AvatarFinish.Ruby -> Brush.verticalGradient(listOf(colors.suitRed, colors.ruby))
    AvatarFinish.Ivory -> SolidColor(colors.ivory)
    AvatarFinish.Emerald -> Brush.verticalGradient(listOf(colors.goldLight, colors.success))
}

/** Nombre accesible del avatar ("Pica dorada"…). */
@Composable
fun avatarName(avatar: AvatarId): String = stringResource(avatar.style.name)

/** Avatar del jugador: palo de la baraja sobre ficha de tapete. */
@Composable
fun AvatarBadge(
    avatar: AvatarId,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val casino = RoyalTheme.casinoColors
    val style = avatar.style
    val name = stringResource(style.name)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(casino.feltBrush)
            .border(BorderStroke(1.5.dp, casino.goldBrush), CircleShape)
            .semantics { contentDescription = name },
    ) {
        SuitGlyph(style.suit, brush = style.finish.brush(casino), modifier = Modifier.size(size * 0.55f))
    }
}

/** Selector de avatar. Cada opción es un botón de radio accesible de 56 dp. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarPicker(
    selected: AvatarId,
    onSelect: (AvatarId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s)) {
        Text(
            text = stringResource(Res.string.avatar_picker_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
            modifier = Modifier.selectableGroup(),
        ) {
            AvatarId.entries.forEach { avatar ->
                val isSelected = avatar == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                        )
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(avatar) })
                        .padding(5.dp),
                ) {
                    AvatarBadge(avatar = avatar, size = 50.dp)
                }
            }
        }
    }
}
