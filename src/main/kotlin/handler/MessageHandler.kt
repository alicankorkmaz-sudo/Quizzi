package handler

import domain.GameEvent
import domain.RoomEvent
import exception.BusinessError
import kotlinx.serialization.json.Json
import request.ClientSocketMessage
import response.ServerSocketMessage
import service.PlayerManagerService
import service.RoomBroadcastService
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
                    val player = PlayerManagerService.INSTANCE.getPlayer(playerId)
                    val roomId =
                        RoomManagerService.INSTANCE.createRoom(
                            "${player.name}'s Room",
                            playerId,
                            clientMessage.categoryId,
                            clientMessage.gameType
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

                    RoomManagerService.INSTANCE.joinRoom(player, clientMessage.roomId)

                    SessionManagerService.INSTANCE.getPlayerSession(playerId)
                        ?.let { RoomBroadcastService.INSTANCE.subscribe(clientMessage.roomId, it) }

                    room.handleEvent(RoomEvent.Joined(player))
                    val response = ServerSocketMessage.JoinedRoom(
                        clientMessage.roomId,
                        success = true
                    )

                    SessionManagerService.INSTANCE.broadcastToPlayers(mutableListOf(playerId), response)
                }

                is ClientSocketMessage.RejoinRoom -> {
                    val room = RoomManagerService.INSTANCE.getRoomById(clientMessage.roomId)
                    val player = PlayerManagerService.INSTANCE.getPlayer(playerId)

                    RoomManagerService.INSTANCE.joinRoom(player, clientMessage.roomId)

                    SessionManagerService.INSTANCE.getPlayerSession(playerId)
                        ?.let { RoomBroadcastService.INSTANCE.subscribe(clientMessage.roomId, it) }

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
        try {
            SessionManagerService.INSTANCE.removePlayerSession(playerId)
            RoomManagerService.INSTANCE.playerDisconnected(playerId)
        } catch (e: BusinessError) {
            println("Business error ${e.message}")
        }
    }
}