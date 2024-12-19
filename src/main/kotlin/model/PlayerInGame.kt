package model

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
class PlayerInGame(val id: String, val name: String, val avatarUrl: String, val index: Int) {

    constructor(player: Player, index: Int) : this(player.id, player.name, player.avatarUrl, index)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val otherPlayer: PlayerInGame = other as PlayerInGame
        return otherPlayer.id == id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}