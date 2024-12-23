package dto

import kotlinx.serialization.Serializable
import state.RoomState

@Serializable
data class GameRoomDTO(
    val id: String,
    val name: String,
    val playerCount: Int,
    val roomState: RoomState,
    val players: List<String>
)