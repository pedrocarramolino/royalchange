package com.royalchance.data.games

import com.royalchance.data.settings.KeyValueStore
import com.royalchance.domain.game.GameSessionStore
import com.royalchance.domain.game.GameType

/** Partidas en curso en el almacén clave-valor del dispositivo, una por jugador y juego. */
class KeyValueGameSessionStore(private val store: KeyValueStore) : GameSessionStore {

    override fun load(playerId: String, game: GameType): String? = store.getString(key(playerId, game))

    override fun save(playerId: String, game: GameType, session: String) {
        store.putString(key(playerId, game), session)
    }

    override fun clear(playerId: String, game: GameType) {
        store.remove(key(playerId, game))
    }

    private fun key(playerId: String, game: GameType) = "session.${game.name}.$playerId"
}
