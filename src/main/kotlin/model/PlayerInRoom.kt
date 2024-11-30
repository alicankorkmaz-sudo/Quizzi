package model

import dto.PlayerDTO
import enums.PlayerState
import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
data class PlayerInRoom(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val index: Int,
    var state: PlayerState
) {
    constructor(player: Player, index: Int) : this(player.id, player.name, player.avatarUrl, index, PlayerState.WAIT)

    constructor(player: Player, index: Int, state: PlayerState) : this(
        player.id,
        player.name,
        player.avatarUrl,
        index,
        state
    )

    fun toPlayerDTO(): PlayerDTO {
        return PlayerDTO(this.id, this.name, this.avatarUrl, this.state)
    }

    fun toPlayer(): Player {
        return Player(this.id, this.name, this.avatarUrl)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val otherPlayer: PlayerInRoom = other as PlayerInRoom
        return otherPlayer.id == id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
