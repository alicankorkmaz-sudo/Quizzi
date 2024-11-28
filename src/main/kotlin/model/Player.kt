package model

import dto.PlayerDTO
import kotlinx.serialization.Serializable


@Serializable
data class Player(val id: String, val name: String, val avatarUrl: String) {
    fun toDTO(): PlayerDTO {
        return PlayerDTO(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val otherPlayer: Player = other as Player
        return otherPlayer.id == id
    }
}
