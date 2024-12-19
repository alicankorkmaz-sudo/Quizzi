package model

import dto.PlayerDTO
import kotlinx.serialization.Serializable


@Serializable
data class Player(val id: String, val name: String, val avatarUrl: String) {
    constructor(playerDTO: PlayerDTO) : this(playerDTO.id, playerDTO.name, playerDTO.avatarUrl)

    fun toDTO(): PlayerDTO {
        return PlayerDTO(this)
    }

    fun toPlayerInRoom(index: Int): PlayerInRoom {
        return PlayerInRoom(this, index)
    }

    fun toPlayerInGame(index: Int): PlayerInGame {
        return PlayerInGame(this, index)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val otherPlayer: Player = other as Player
        return otherPlayer.id == id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
