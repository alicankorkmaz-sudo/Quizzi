package dto

import state.PlayerState
import kotlinx.serialization.Serializable
import model.Player
import model.PlayerInRoom

/**
 * @author guvencenanguvenal
 */
@Serializable
data class PlayerDTO(val id: String, val name: String, val avatarUrl: String, var state: PlayerState) {
    constructor(player: Player) : this(player.id, player.name, player.avatarUrl, PlayerState.WAIT)

    constructor(player: PlayerInRoom) : this(player.id, player.name, player.avatarUrl, player.state)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val otherPlayer: PlayerDTO = other as PlayerDTO
        return otherPlayer.id == id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}