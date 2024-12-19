package service

import dto.GameRoomDTO
import model.GameRoom
import model.Player
import service.internal.RoomService

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

    suspend fun createRoom(name: String, playerId: String, gameType: String): String {
        val creatorPlayer = PlayerManagerService.INSTANCE.getPlayer(playerId)
        val room = roomService.createRoom(name, creatorPlayer, GameFactory.CategoryType.FLAGS, gameType)
        room.broadcastRoomState()
        return room.id
    }

    fun joinRoom(player: Player, roomId: String): Boolean = roomService.joinRoom(player, roomId)

    suspend fun playerDisconnected(playerId: String) {
        roomService.playerDisconnected(playerId)
    }

    fun getActiveRooms(): List<GameRoomDTO> {
        return roomService.getAllRooms().map { (id, room) ->
            GameRoomDTO(
                id = id,
                name = room.name,
                playerCount = room.getPlayerCount(),
                roomState = room.getState(),
                players = room.getPlayerNames()
            )
        }
    }
}