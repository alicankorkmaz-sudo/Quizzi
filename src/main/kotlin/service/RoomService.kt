package service

import model.Game
import model.GameRoom
import model.Player
import model.RoomState
import response.DisconnectedPlayer
import java.util.*

/**
 * @author guvencenanguvenal
 */
class RoomService {

    private val rooms = Collections.synchronizedMap(mutableMapOf<String, GameRoom>())

    private val playerToRoom = Collections.synchronizedMap(mutableMapOf<String, String>())

    private val disconnectedPlayers = Collections.synchronizedMap(mutableMapOf<String, DisconnectedPlayer>())

    fun createRoom(creator: Player, game: Game): String {
        val roomId = UUID.randomUUID().toString()
        val room = GameRoom(roomId, game)
        room.players.add(creator)
        rooms[roomId] = room
        playerToRoom[creator.id] = roomId
        println("Room $roomId created by player ${creator.id}")
        return roomId
    }

    fun joinRoom(player: Player, roomId: String): Boolean {
        val room = rooms[roomId] ?: return false
        //TODO odaya istedigi kadar kisi katilabilecek
        if (room.players.size >= 2) return false
        room.players.add(player)
        playerToRoom[player.id] = roomId
        return true
    }

    fun rejoinRoom(player: Player, roomId: String): Boolean {
        val room = rooms[roomId] ?: return false
        disconnectedPlayers[player.id] ?: return false
        room.roomState = RoomState.PLAYING
        playerToRoom[player.id] = roomId
        return true
    }

    private suspend fun cleanupRoom(room: GameRoom) {
        room.players.forEach { player -> SessionManagerService.INSTANCE.removePlayerSession(player.id) }
        // Oda verilerini temizle
        if (room.rounds.size > 0) {
            room.rounds.last().timer?.cancel()
        }
        rooms.remove(room.id)
    }

}