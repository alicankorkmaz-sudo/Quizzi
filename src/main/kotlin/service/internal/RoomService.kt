package service.internal

import exception.RoomNotFound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.GameRoom
import model.Player
import model.ResistanceGame
import response.DisconnectedPlayer
import response.ServerSocketMessage
import service.GameFactory
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

    fun getRoomByPlayerId(playerId: String) = rooms[playerToRoom[playerId]] ?: throw RoomNotFound("from PlayerId")

    fun getRoomIdByPlayerId(playerId: String) = playerToRoom[playerId] ?: throw RoomNotFound("from PlayerId")

    fun createRoom(roomName: String, creator: Player, gameCategoryId: Int, gameType: String): GameRoom {
        val roomId = UUID.randomUUID().toString()
        val game = GameFactory.INSTANCE.createGame(roomId, gameCategoryId, gameType, roomId)
        val room = GameRoom(roomId, roomName, game)
        room.addPlayer(creator)
        rooms[roomId] = room
        playerToRoom[creator.id] = roomId
        println("Room $roomId created by player ${creator.id}")
        return room
    }

    fun joinRoom(player: Player, roomId: String): Boolean {
        playerToRoom[player.id] = roomId
        return true
    }

    suspend fun cleanupRoom(room: GameRoom) {
        room.getPlayers().forEach { player -> SessionManagerService.INSTANCE.removePlayerSession(player.id) }
        // Oda verilerini temizle
        if (room.game.rounds.size > 0) {
            room.game.rounds.filter { r -> r.job?.isActive == true }.forEach { r -> r.job?.cancel() }
        }
        rooms.remove(room.id)
    }

    suspend fun playerDisconnected(disconnectedPlayerId: String) {
        try {
            val roomId = getRoomIdByPlayerId(disconnectedPlayerId)
            val room = getRoomById(roomId)

            val disconnectedPlayer = PlayerManagerService.INSTANCE.getPlayer(disconnectedPlayerId)

            val resistanceGame = room.game as ResistanceGame

            val roundEndMessage = ServerSocketMessage.RoundEnded(
                cursorPosition = resistanceGame.cursorPosition,
                correctAnswer = room.game.getLastRound().question.answer,
                winnerPlayerId = null
            )
            room.broadcast(roundEndMessage)

            //room.roomEnumState = RoomEnumState.PAUSED
            room.broadcastRoomState()

            disconnectedPlayers[disconnectedPlayerId] = DisconnectedPlayer(
                playerId = disconnectedPlayerId,
                playerName = disconnectedPlayer.name,
                roomId = roomId
            )
            room.removePlayer(disconnectedPlayer.id)
            playerToRoom.remove(disconnectedPlayerId)

            if (room.getPlayerCount() == 0) {
                cleanupRoom(room)
                return
            }

            val disconnectMessage = ServerSocketMessage.PlayerDisconnected(
                playerId = disconnectedPlayer.id,
                playerName = disconnectedPlayer.name
            )
            room.broadcast(disconnectMessage)

            room.game.rounds.filter { r -> r.job?.isActive == true }.forEach { r -> r.job?.cancel() }
            room.game.rounds.removeAt(room.game.rounds.size - 1)

            CoroutineScope(Dispatchers.Default).launch {
                delay(30000)
                // if (room.roomEnumState == RoomEnumState.PAUSED) {
                disconnectedPlayers.remove(disconnectedPlayerId)
                println("Player $disconnectedPlayerId did not reconnect within 30 seconds, cleaning up room $roomId")
                room.broadcast(ServerSocketMessage.RoomClosed(reason = "Player disconnected for too long"))
                cleanupRoom(room)
                //}
            }
        } catch (e: RoomNotFound) {
            return
        }
    }
}