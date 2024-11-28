package service.internal

import exception.PlayerSessionNotFound
import model.Player
import java.util.*

/**
 * @author guvencenanguvenal
 */
class PlayerService {

    private val players = Collections.synchronizedMap(mutableMapOf<String, Player>())

    fun createPlayer(name: String, avatarUrl: String): Player {
        val id = UUID.randomUUID().toString()
        val player = Player(id, name, avatarUrl)
        players[id] = player
        return player
    }

    fun deletePlayer(id: String) = players.remove(id)

    fun getPlayer(id: String) = players[id] ?: throw PlayerSessionNotFound(id)
}