package model

import dto.PlayerDTO
import enums.RoomState
import kotlinx.serialization.Serializable

@Serializable
data class GameRoom(
    val id: String,
    val name: String,
    val game: Game,
    val players: MutableList<PlayerDTO> = mutableListOf(),
    var roomState: RoomState = RoomState.WAITING,
)