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
            when (val clientMessage = json.decodeFromString<ClientSocketMessage>(message)) {
                is ClientSocketMessage.CreateRoom -> {
                    val roomId = RoomManagerService.INSTANCE.createRoom(playerId)
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
                    if (success) {
                        RoomManagerService.INSTANCE.startGame(clientMessage.roomId)
                    }
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
                        RoomManagerService.INSTANCE.continueGame(clientMessage.roomId)
                    }
                }

                is ClientSocketMessage.PlayerReady -> {

                }

                is ClientSocketMessage.PlayerAnswer -> {
                    val roomId = RoomManagerService.INSTANCE.getRoomIdFromPlayerId(playerId)
                    RoomManagerService.INSTANCE.playerAnswered(roomId, playerId, clientMessage.answer)
                }
            }
        } catch (e: Exception) {
            println("Error processing message: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun handleDisconnect(playerId: String) {
        SessionManagerService.INSTANCE.removePlayerSession(playerId)
        RoomManagerService.INSTANCE.playerDisconnected(playerId)
    }
}