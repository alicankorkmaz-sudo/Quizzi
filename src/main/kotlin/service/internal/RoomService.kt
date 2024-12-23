package service.internal

import domain.RoomEvent
import exception.RoomIsEmpty
import exception.RoomNotFound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.GameRoom
import model.Player
import response.DisconnectedPlayer
import service.GameFactory
import service.RoomBroadcastService
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

    suspend fun createRoom(roomName: String, creator: Player, gameCategoryId: Int, gameType: String): GameRoom {
        val roomId = UUID.randomUUID().toString()
        val game = GameFactory.INSTANCE.createGame(roomId, gameCategoryId, gameType, roomId)
        val room = GameRoom(roomId, roomName, game)
        rooms[roomId] = room
        playerToRoom[creator.id] = roomId
        room.handleEvent(RoomEvent.Created(creator))
        println("Room $roomId created by player ${creator.id}")
        return room
    }

    fun joinRoom(player: Player, roomId: String): Boolean {
        playerToRoom[player.id] = roomId
        return true
    }

    suspend fun closeRoom(room: GameRoom) {
        room.getPlayers().forEach { player ->
            SessionManagerService.INSTANCE.removePlayerSession(player.id)
            playerToRoom.remove(player.id)
        }
        RoomBroadcastService.INSTANCE.deleteRoom(room.id)
        rooms.remove(room.id)
        print("${room.id} room is cleaned!")
    }

    suspend fun playerDisconnected(disconnectedPlayerId: String) {
        try {
            val room = getRoomByPlayerId(disconnectedPlayerId)

            try {
                room.handleEvent(RoomEvent.Disconnected(disconnectedPlayerId))
            } catch (_: RoomIsEmpty) {
                room.transitionTo(RoomState.Closing)
                return
            }

            disconnectedPlayers[disconnectedPlayerId] = DisconnectedPlayer(
                playerId = disconnectedPlayerId,
                roomId = room.id
            )
            playerToRoom.remove(disconnectedPlayerId)

            CoroutineScope(Dispatchers.Default).launch {
                delay(20000)
                if (room.getState() is RoomState.Pausing) {
                    room.transitionTo(RoomState.Closing)
                    println("Player $disconnectedPlayerId did not reconnect within 30 seconds, cleaning up room ${room.id}")
                }
                disconnectedPlayers.remove(disconnectedPlayerId)
            }
        } catch (e: RoomNotFound) {
            return
        }
    }
}