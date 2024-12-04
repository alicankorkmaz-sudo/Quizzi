package model

import dto.PlayerDTO
import enums.PlayerState
import enums.RoomState
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class GameRoom(
    val id: String,
    val name: String,
    val game: Game,
    val players: MutableList<PlayerDTO> = Collections.synchronizedList(mutableListOf()),
    var roomState: RoomState = RoomState.WAITING,
) {
    fun removePlayer(playerId: String) {
        players.removeIf { p -> p.id == playerId }
    }

    fun isAllPlayerReady(): Boolean {
        val notReadyPlayers = players.filter { player -> player.state == PlayerState.WAIT }.size
        return (notReadyPlayers == 0) && (game.maxPlayerCount() == players.size)
    }
}