package model

import dto.PlayerDTO
import kotlinx.serialization.Serializable

@Serializable
data class Player(val id: String, val name: String, val avatarUrl: String) {
    fun toDTO(): PlayerDTO {
        return PlayerDTO(this)
    }
}
