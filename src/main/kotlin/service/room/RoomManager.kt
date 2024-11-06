package service.room

import models.Player
import models.GameState
import models.GameRoom
import models.ActiveRoom
import java.util.*

class RoomManager {
    private val rooms = mutableMapOf<String, GameRoom>()
    private val playerToRoom = mutableMapOf<String, String>()

    fun createRoom(playerId: String, playerName: String): String {
        val roomId = UUID.randomUUID().toString()
        val player = Player(playerId, playerName)
        val room = GameRoom(roomId)
        room.players.add(player)
        rooms[roomId] = room
        playerToRoom[playerId] = roomId
        return roomId
    }

    fun joinRoom(playerId: String, roomId: String, playerName: String): Boolean {
        val room = rooms[roomId] ?: return false
        if (room.players.size >= 2) return false

        val player = Player(playerId, playerName)
        room.players.add(player)
        playerToRoom[playerId] = roomId
        return true
    }

    fun updateGameState(roomId: String, gameState: GameState) {
        rooms[roomId]?.gameState = gameState
    }

    fun updateCursorPosition(roomId: String, delta: Float) {
        rooms[roomId]?.let { room ->
            room.cursorPosition += delta
        }
    }

    fun getActiveRooms(): List<ActiveRoom> = rooms.map { (id, room) ->
        ActiveRoom(
            id = id,
            playerCount = room.players.size,
            gameState = room.gameState,
            players = room.players.map { it.name }
        )
    }

    fun removeRoom(roomId: String) {
        val room = rooms[roomId] ?: return
        room.players.forEach { player ->
            playerToRoom.remove(player.id)
        }
        rooms.remove(roomId)
    }

    fun getRoom(roomId: String): GameRoom? = rooms[roomId]
    fun getPlayerRoom(playerId: String): String? = playerToRoom[playerId]
    fun removePlayerRoom(playerId: String) = playerToRoom.remove(playerId)
} 