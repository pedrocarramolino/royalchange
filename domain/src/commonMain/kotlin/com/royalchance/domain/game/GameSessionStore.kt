package com.royalchance.domain.game

/**
 * Partida en curso guardada en el dispositivo (p. ej. una mano de Blackjack a medias), para
 * reanudarla al volver. Se guarda como texto: el formato lo decide cada juego.
 *
 * Es local a propósito: las fichas en juego ya están en el monedero (en la nube) como ronda
 * abierta; aquí solo va el estado de la mesa.
 */
interface GameSessionStore {
    fun load(playerId: String, game: GameType): String?

    fun save(playerId: String, game: GameType, session: String)

    fun clear(playerId: String, game: GameType)
}
