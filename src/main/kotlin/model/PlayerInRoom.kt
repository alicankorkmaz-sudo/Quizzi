package model

import dto.PlayerDTO
import kotlinx.serialization.Serializable
import state.PlayerState

/**
 * @author guvencenanguvenal
 */
@Serializable
class PlayerInRoom(val id: String, val name: String, val avatarUrl: String, val index: Int, var state: PlayerState) {

    constructor(player: Player, index: Int) : this(player.id, player.name, player.avatarUrl, index, PlayerState.WAIT)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val otherPlayer: PlayerInRoom = other as PlayerInRoom
        return otherPlayer.id == id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    fun toDTO(): PlayerDTO {
        return PlayerDTO(this)
    }
}