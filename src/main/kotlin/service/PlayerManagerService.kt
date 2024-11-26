package service

import model.Player
import service.internal.PlayerService

/**
 * @author guvencenanguvenal
 */
class PlayerManagerService private constructor() {
    companion object {
        val INSTANCE: PlayerManagerService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { PlayerManagerService() }
    }

    private val playerService: PlayerService = PlayerService()

    fun createPlayer(name: String, avatarUrl: String): Player = playerService.createPlayer(name, avatarUrl)

    fun deletePlayer(id: String) = playerService.deletePlayer(id)

    fun getPlayer(id: String) = playerService.getPlayer(id)

}