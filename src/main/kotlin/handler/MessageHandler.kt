package handler

import kotlinx.serialization.json.Json
import request.ClientSocketMessage
import response.ServerSocketMessage
import service.GameFactory
import service.PlayerManagerService
import service.RoomManagerService
import service.SessionManagerService

/**
 * @author guvencenanguvenal
 */
class MessageHandler private constructor() {
    companion object {
        val INSTANCE: MessageHandler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { MessageHandler() }
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun handleMessage(playerId: String, message: String) {
        when (val clientMessage = json.decodeFromString<ClientSocketMessage>(message)) {
            is ClientSocketMessage.CreateRoom -> {
                val player = PlayerManagerService.INSTANCE.getPlayer(playerId)
                val roomId =
                    RoomManagerService.INSTANCE.createRoom(
                        "${player.id} 's Room",
                        playerId,
                        GameFactory.GameType.RESISTANCE_GAME
                    )
                val response = ServerSocketMessage.RoomCreated(
                    roomId = roomId
                )
                SessionManagerService.INSTANCE.broadcastToPlayers(mutableListOf(playerId), response)
            }

            is ClientSocketMessage.JoinRoom -> {
                val success = RoomManagerService.INSTANCE.joinRoom(playerId, clientMessage.roomId)
                val response = ServerSocketMessage.JoinedRoom(
                    clientMessage.roomId,
                    success = success
                )
                SessionManagerService.INSTANCE.broadcastToPlayers(mutableListOf(playerId), response)
            }

            is ClientSocketMessage.RejoinRoom -> {
                val success = RoomManagerService.INSTANCE.rejoinRoom(playerId, clientMessage.roomId)
                val response = ServerSocketMessage.RejoinedRoom(
                    clientMessage.roomId,
                    playerId,
                    success = success
                )
                SessionManagerService.INSTANCE.broadcastToPlayers(mutableListOf(playerId), response)
                if (success) {
                    RoomManagerService.INSTANCE.startGame(clientMessage.roomId)
                }
            }

            is ClientSocketMessage.PlayerReady -> {
                val isAllPlayersReady = RoomManagerService.INSTANCE.playerReady(playerId)
                if (isAllPlayersReady) {
                    val roomId = RoomManagerService.INSTANCE.getRoomIdFromPlayerId(playerId)
                    RoomManagerService.INSTANCE.startGame(roomId)
                }
            }

            is ClientSocketMessage.PlayerAnswer -> {
                val roomId = RoomManagerService.INSTANCE.getRoomIdFromPlayerId(playerId)
                RoomManagerService.INSTANCE.playerAnswered(roomId, playerId, clientMessage.answer)
            }
        }
    }

    suspend fun handleDisconnect(playerId: String) {
        SessionManagerService.INSTANCE.removePlayerSession(playerId)
        RoomManagerService.INSTANCE.playerDisconnected(playerId)
    }
}