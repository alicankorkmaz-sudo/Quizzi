package dto

import kotlinx.serialization.Serializable
import enums.RoomStateEnum

@Serializable
data class GameRoomDTO(
    val id: String,
    val name: String,
    val playerCount: Int,
    val roomStateEnum: RoomStateEnum,
    val players: List<String>
)