package handler

import domain.GameEvent
import domain.RoomEvent
import exception.BusinessError
import kotlinx.serialization.json.Json
import request.ClientSocketMessage
import response.ServerSocketMessage
import service.*
import service.internal.RoomService

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
                    val player = PlayerManagerService.INSTANCE.getPlayer(playerId)
                    val roomId =
                        RoomManagerService.INSTANCE.createRoom(
                            "${player.name}'s Room",
                            playerId,
                            GameFactory.GameType.RESISTANCE_GAME
                        )
                    val response = ServerSocketMessage.RoomCreated(
                        roomId = roomId
                    )
                    SessionManagerService.INSTANCE.getPlayerSession(playerId)
                        ?.let { RoomBroadcastService.INSTANCE.subscribe(roomId, it) }
                    SessionManagerService.INSTANCE.broadcastToPlayers(mutableListOf(playerId), response)
                }

                is ClientSocketMessage.JoinRoom -> {
                    val room = RoomManagerService.INSTANCE.getRoomById(clientMessage.roomId)
                    val player = PlayerManagerService.INSTANCE.getPlayer(playerId)
                    room.handleEvent(RoomEvent.Joined(player))
                    val response = ServerSocketMessage.JoinedRoom(
                        clientMessage.roomId,
                        success = true
                    )
                    SessionManagerService.INSTANCE.getPlayerSession(playerId)
                        ?.let { RoomBroadcastService.INSTANCE.subscribe(clientMessage.roomId, it) }
                    SessionManagerService.INSTANCE.broadcastToPlayers(mutableListOf(playerId), response)
                }

                is ClientSocketMessage.RejoinRoom -> {
                    val room = RoomManagerService.INSTANCE.getRoomById(clientMessage.roomId)
                    val player = PlayerManagerService.INSTANCE.getPlayer(playerId)
                    room.handleEvent(RoomEvent.Joined(player))
                    room.handleEvent(RoomEvent.Ready(playerId))
                }

                is ClientSocketMessage.PlayerReady -> {
                    val room = RoomManagerService.INSTANCE.getRoomByPlayerId(playerId)
                    room.handleEvent(RoomEvent.Ready(playerId))
                }

                is ClientSocketMessage.PlayerAnswer -> {
                    val room = RoomManagerService.INSTANCE.getRoomByPlayerId(playerId)
                    val player = PlayerManagerService.INSTANCE.getPlayer(playerId)

                    room.game.handleEvent(GameEvent.RoundAnswered(player, clientMessage.answer))
                }
            }
        } catch (e: BusinessError) {
            println("Business error ${e.message}")
        }
    }

    suspend fun handleDisconnect(playerId: String) {
        SessionManagerService.INSTANCE.removePlayerSession(playerId)
        RoomManagerService.INSTANCE.playerDisconnected(playerId)
    }
}