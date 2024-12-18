package dto

import kotlinx.serialization.Serializable
import enums.RoomEnumState

@Serializable
data class GameRoomDTO(
    val id: String,
    val name: String,
    val playerCount: Int,
    val roomEnumState: RoomEnumState,
    val players: List<String>
)