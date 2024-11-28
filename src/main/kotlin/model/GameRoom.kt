package model

import dto.PlayerDTO
import enums.RoomState
import kotlinx.serialization.Serializable

@Serializable
data class GameRoom(
    val id: String,
    val game: Game,
    val players: MutableList<PlayerDTO> = mutableListOf(),
    val rounds: MutableList<Round> = mutableListOf(),
    var roomState: RoomState = RoomState.WAITING,
    var cursorPosition: Float = 0.5f
)