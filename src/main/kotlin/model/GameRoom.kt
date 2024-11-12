package model

import kotlinx.serialization.Serializable

@Serializable
data class GameRoom(
    val id: String,
    val players: MutableList<Player> = mutableListOf(),
    val rounds: MutableList<Round> = mutableListOf(),
    var roomState: RoomState = RoomState.WAITING,
    var game: Game? = null,
    var cursorPosition: Float = 0.5f
)