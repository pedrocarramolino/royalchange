package com.royalchance.core.ui

import androidx.compose.runtime.Composable
import com.royalchance.core.ui.resources.Res
import com.royalchance.core.ui.resources.game_name_blackjack
import com.royalchance.core.ui.resources.game_name_dice
import com.royalchance.core.ui.resources.game_name_poker
import com.royalchance.core.ui.resources.game_name_roulette
import com.royalchance.core.ui.resources.game_name_slots
import com.royalchance.domain.game.GameType
import org.jetbrains.compose.resources.stringResource

/** Nombre de un juego para mostrar. */
@Composable
fun gameName(game: GameType): String = stringResource(
    when (game) {
        GameType.Blackjack -> Res.string.game_name_blackjack
        GameType.Roulette -> Res.string.game_name_roulette
        GameType.Slots -> Res.string.game_name_slots
        GameType.Poker -> Res.string.game_name_poker
        GameType.Dice -> Res.string.game_name_dice
    },
)
