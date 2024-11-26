package service

import model.Player
import response.ErrorMessage
import java.util.*

/**
 * @author guvencenanguvenal
 */
class PlayerManagerService private constructor() {
    companion object {
        val INSTANCE: PlayerManagerService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { PlayerManagerService() }
    }

    private val players = Collections.synchronizedMap(mutableMapOf<String, Player>())

    fun createPlayer(name: String, avatarUrl: String) : Player {
        val id = UUID.randomUUID().toString()
        val player = Player(id, name, avatarUrl)
        players[id] = player
        return player
    }

    fun deletePlayer(id: String) = players.remove(id)

    fun getPlayer(id: String) = players[id] ?: throw ErrorMessage.PlayerSessionNotFound(id)
}