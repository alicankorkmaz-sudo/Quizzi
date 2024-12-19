package service.internal

import domain.RoomEvent
import exception.RoomNotFound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.GameRoom
import model.Player
import response.DisconnectedPlayer
import response.ServerSocketMessage
import service.GameFactory
import service.PlayerManagerService
import service.SessionManagerService
import state.RoomState
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
        rooms.remove(room.id)
    }

    suspend fun playerDisconnected(disconnectedPlayerId: String) {
        try {
            val roomId = getRoomIdByPlayerId(disconnectedPlayerId)
            val room = getRoomById(roomId)

            room.handleEvent(RoomEvent.Disconnected(disconnectedPlayerId))

            disconnectedPlayers[disconnectedPlayerId] = DisconnectedPlayer(
                playerId = disconnectedPlayerId,
                roomId = roomId
            )
            playerToRoom.remove(disconnectedPlayerId)

            CoroutineScope(Dispatchers.Default).launch {
                delay(30000)
                if (room.getState() == RoomState.Pausing) {
                    room.transitionTo(RoomState.Closing)
                    disconnectedPlayers.remove(disconnectedPlayerId)
                    println("Player $disconnectedPlayerId did not reconnect within 30 seconds, cleaning up room $roomId")
                    cleanupRoom(room)
                }
            }
        } catch (e: RoomNotFound) {
            return
        }
    }
}