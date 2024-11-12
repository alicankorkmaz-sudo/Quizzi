package handler

import kotlinx.serialization.json.Json
import request.ClientSocketMessage
import response.ServerSocketMessage
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
        try {
            when (val gameMessage = json.decodeFromString<ClientSocketMessage>(message)) {
                is ClientSocketMessage.CreateRoom -> {
                    val roomId = RoomManagerService.INSTANCE.createRoom(playerId)
                    val response = ServerSocketMessage.RoomCreated(
                        roomId = roomId
                    )
                    SessionManagerService.INSTANCE.broadcastToPlayers(mutableListOf(playerId), response)
                }

                is ClientSocketMessage.JoinRoom -> {
                    val success = RoomManagerService.INSTANCE.joinRoom(playerId, gameMessage.roomId)
                    val response = ServerSocketMessage.JoinedRoom(
                        gameMessage.roomId,
                        success = success
                    )
                    SessionManagerService.INSTANCE.broadcastToPlayers(mutableListOf(playerId), response)
                    if (success) {
                        RoomManagerService.INSTANCE.startGame(gameMessage.roomId)
                    }
                }

                is ClientSocketMessage.PlayerAnswer -> {
                    val roomId = RoomManagerService.INSTANCE.getRoomIdFromPlayerId(playerId)
                    RoomManagerService.INSTANCE.playerAnswered(roomId, playerId, gameMessage.answer)
                }

                else -> {
                    println("Unexpected message type received: ${gameMessage::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            println("Error processing message: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun handleDisconnect(playerId: String) {
        RoomManagerService.INSTANCE.playerDisconnected(playerId)
        SessionManagerService.INSTANCE.removePlayerSession(playerId)
    }
}