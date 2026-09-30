package com.royalchance.domain.game

/** Juegos del casino. Los ids son estables: se usarán en historial, estadísticas y sincronización. */
enum class GameType {
    Blackjack,
    Roulette,
    Slots,
    Poker,
    Dice,
}
