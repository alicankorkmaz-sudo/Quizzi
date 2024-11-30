package dto

import kotlinx.serialization.Serializable
import enums.RoomState

@Serializable
data class GameRoomDTO(
    val id: String,
    val name: String,
    val playerCount: Int,
    val roomState: RoomState,
    val players: List<String>
)