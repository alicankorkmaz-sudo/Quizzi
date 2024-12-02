package model

import dto.PlayerDTO
import enums.PlayerState
import enums.RoomState
import kotlinx.serialization.Serializable

@Serializable
data class GameRoom(
    val id: String,
    val name: String,
    val game: Game,
    val players: MutableList<PlayerDTO> = mutableListOf(),
    var roomState: RoomState = RoomState.WAITING,
) {
    fun isAllPlayerReady(): Boolean {
        val notReadyPlayers = players.filter { player -> player.state == PlayerState.WAIT }.size
        return (notReadyPlayers == 0) && (game.maxPlayerCount() == players.size)
    }
}