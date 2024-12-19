package service

import dto.GameRoomDTO
import exception.RoomNotFound
import model.GameRoom
import response.ServerSocketMessage
import service.internal.RoomService
import java.util.*

/**
 * @author guvencenanguvenal
 */
class RoomManagerService private constructor() {
    companion object {
        val INSTANCE: RoomManagerService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomManagerService() }
    }

    private val roomService: RoomService = RoomService()

    fun getAllRooms(): MutableMap<String, GameRoom> = roomService.getAllRooms()

    fun getRoomById(id: String) = roomService.getRoomById(id)

    fun getRoomByPlayerId(playerId: String) = roomService.getRoomByPlayerId(playerId)

    fun getRoomIdByPlayerId(playerId: String) = roomService.getRoomIdByPlayerId(playerId)

    suspend fun createRoom(name: String, playerId: String, gameType: String): String {
        val creatorPlayer = PlayerManagerService.INSTANCE.getPlayer(playerId)
        val roomId = roomService.createRoom(name, creatorPlayer, GameFactory.CategoryType.FLAGS, gameType)
        broadcastRoomState(roomId)
        return roomId
    }

    suspend fun playerDisconnected(playerId: String) {
        roomService.playerDisconnected(playerId)
    }

    fun getActiveRooms(): List<GameRoomDTO> {
        return roomService.getAllRooms().map { (id, room) ->
            GameRoomDTO(
                id = id,
                name = room.name,
                playerCount = room.players.size,
                roomEnumState = room.roomEnumState,
                players = room.players.map { it.name }
            )
        }
    }

    private suspend fun broadcastRoomState(roomId: String) {
        println("Broadcasting game state for room $roomId")
        val room = roomService.getRoomById(roomId)

        val gameUpdate = ServerSocketMessage.RoomUpdate(
            players = room.players,
            state = room.roomEnumState,
        )
        room.broadcast(gameUpdate)
    }
}