package service.internal

import dto.PlayerDTO
import enums.PlayerState
import enums.RoomState
import exception.RoomNotFound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.Game
import model.GameRoom
import model.Player
import model.ResistanceGame
import response.DisconnectedPlayer
import response.ServerSocketMessage
import service.PlayerManagerService
import service.SessionManagerService
import java.util.*

/**
 * @author guvencenanguvenal
 */
class RoomService {

    private val rooms = Collections.synchronizedMap(mutableMapOf<String, GameRoom>())

    private val playerToRoom = Collections.synchronizedMap(mutableMapOf<String, String>())

    private val disconnectedPlayers = Collections.synchronizedMap(mutableMapOf<String, DisconnectedPlayer>())

    fun getAllRooms(): MutableMap<String, GameRoom> = rooms

    fun getRoomById(id: String) = rooms[id] ?: throw RoomNotFound(id)

    fun getRoomIdFromPlayerId(playerId: String) = playerToRoom[playerId] ?: throw RoomNotFound("from PlayerId")

    fun createRoom(roomName: String, creator: Player, game: Game): String {
        val roomId = UUID.randomUUID().toString()
        val room = GameRoom(roomId, roomName, game)
        room.players.add(creator.toDTO())
        rooms[roomId] = room
        playerToRoom[creator.id] = roomId
        println("Room $roomId created by player ${creator.id}")
        return roomId
    }

    fun joinRoom(player: Player, roomId: String): Boolean {
        val room = rooms[roomId] ?: throw RoomNotFound(roomId)
        if (room.players.size >= room.game.maxPlayerCount()) return false
        room.players.add(player.toDTO())
        playerToRoom[player.id] = roomId
        return true
    }

    fun rejoinRoom(player: Player, roomId: String): Boolean {
        val room = rooms[roomId] ?: throw RoomNotFound(roomId)
        disconnectedPlayers[player.id] ?: return false
        room.players.add(player.toDTO())
        playerToRoom[player.id] = roomId
        playerReady(player.id)
        return true
    }

    suspend fun cleanupRoom(room: GameRoom) {
        room.players.forEach { player -> SessionManagerService.INSTANCE.removePlayerSession(player.id) }
        // Oda verilerini temizle
        if (room.game.rounds.size > 0) {
            room.game.rounds.last().timer?.cancel()
        }
        rooms.remove(room.id)
    }

    fun playerReady(playerId: String) {
        val roomId = getRoomIdFromPlayerId(playerId)
        val room = getRoomById(roomId)
        room.players.filter { player -> player.id == playerId }.forEach { player -> player.state = PlayerState.READY }
    }

    fun isAllPlayerReady(roomId: String): Boolean {
        val room = getRoomById(roomId)
        val notReadyPlayers = room.players.filter { player -> player.state == PlayerState.WAIT }.size
        return (notReadyPlayers == 0) && (room.game.maxPlayerCount() == room.players.size)
    }

    suspend fun playerDisconnected(disconnectedPlayerId: String) {
        val roomId = getRoomIdFromPlayerId(disconnectedPlayerId)
        val room = getRoomById(roomId)
        val disconnectedPlayer = PlayerManagerService.INSTANCE.getPlayer(disconnectedPlayerId)
        disconnectedPlayers[disconnectedPlayerId] = DisconnectedPlayer(
            playerId = disconnectedPlayerId,
            playerName = disconnectedPlayer.name,
            roomId = roomId
        )
        room.players.remove(disconnectedPlayer.toDTO())
        playerToRoom.remove(disconnectedPlayerId)

        if (room.players.size == 0) {
            cleanupRoom(room)
            return
        }

        val playersInRoom = room.players.filter { it.id != disconnectedPlayerId }.map(PlayerDTO::id).toMutableList()
        val resistanceGame = room.game as ResistanceGame

        val roundEndMessage = ServerSocketMessage.RoundEnded(
            cursorPosition = resistanceGame.cursorPosition,
            correctAnswer = room.game.currentQuestion!!.answer,
            winnerPlayerId = null
        )
        SessionManagerService.INSTANCE.broadcastToPlayers(playersInRoom, roundEndMessage)

        val disconnectMessage = ServerSocketMessage.PlayerDisconnected(
            playerId = disconnectedPlayer.id,
            playerName = disconnectedPlayer.name
        )
        SessionManagerService.INSTANCE.broadcastToPlayers(playersInRoom, disconnectMessage)

        room.roomState = RoomState.PAUSED
        room.game.rounds.last().timer?.cancel()
        room.game.rounds.removeAt(room.game.rounds.size - 1)

        CoroutineScope(Dispatchers.Default).launch {
            delay(30000)
            if (room.roomState == RoomState.PAUSED) {
                disconnectedPlayers.remove(disconnectedPlayerId)
                println("Player $disconnectedPlayerId did not reconnect within 30 seconds, cleaning up room $roomId")
                val message = ServerSocketMessage.RoomClosed(reason = "Player disconnected for too long")
                SessionManagerService.INSTANCE.broadcastToPlayers(
                    room.players.map { disconnectedPlayerId }.toMutableList(),
                    message
                )
                SessionManagerService.INSTANCE.removePlayerSession(disconnectedPlayer.id)
                cleanupRoom(room)
            }
        }
    }

}