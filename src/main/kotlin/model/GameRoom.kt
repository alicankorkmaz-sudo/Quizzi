package model

import enums.RoomState
import kotlinx.serialization.Serializable

@Serializable
data class GameRoom(
    val id: String,
    val name: String,
    val game: Game,
    val players: MutableList<PlayerInRoom> = mutableListOf(),
    var roomState: RoomState = RoomState.WAITING,
)