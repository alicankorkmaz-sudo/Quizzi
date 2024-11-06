package service.player

import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import models.DisconnectedPlayer
import models.GameMessage
import models.GameMessage.ConnectionStateType

class PlayerManager(
    private val scope: CoroutineScope,
    private val json: Json
) {
    private val playerSessions = mutableMapOf<String, DefaultWebSocketSession>()
    private val disconnectedPlayers = mutableMapOf<String, DisconnectedPlayer>()

    suspend fun registerSession(playerId: String, session: DefaultWebSocketSession) {
        playerSessions[playerId] = session
    }

    fun markDisconnected(playerId: String, playerName: String, roomId: String) {
        disconnectedPlayers[playerId] = DisconnectedPlayer(
            playerId = playerId,
            playerName = playerName,
            roomId = roomId
        )
    }

    suspend fun notifyDisconnection(playerId: String, playerName: String, otherPlayers: List<String>) {
        val disconnectMessage = GameMessage.ConnectionState(
            connectionStateType = ConnectionStateType.DISCONNECTED,
            playerId = playerId,
            playerName = playerName
        )

        otherPlayers.forEach { otherId ->
            playerSessions[otherId]?.send(
                Frame.Text(json.encodeToString(GameMessage.serializer(), disconnectMessage))
            )
        }
    }

    suspend fun notifyReconnection(playerId: String, playerName: String, otherPlayers: List<String>) {
        val reconnectMessage = GameMessage.ConnectionState(
            connectionStateType = ConnectionStateType.RECONNECT_SUCCESS,
            playerId = playerId,
            playerName = playerName
        )

        otherPlayers.forEach { otherId ->
            playerSessions[otherId]?.send(
                Frame.Text(json.encodeToString(GameMessage.serializer(), reconnectMessage))
            )
        }
    }

    suspend fun sendToPlayer(playerId: String, message: String) {
        playerSessions[playerId]?.send(Frame.Text(message))
    }

    fun removePlayer(playerId: String) {
        playerSessions.remove(playerId)
        disconnectedPlayers.remove(playerId)
    }

    fun reconnectPlayer(playerId: String, session: DefaultWebSocketSession): DisconnectedPlayer? {
        val disconnectedPlayer = disconnectedPlayers[playerId] ?: return null
        playerSessions[playerId] = session
        disconnectedPlayers.remove(playerId)
        return disconnectedPlayer
    }

    fun getSession(playerId: String): DefaultWebSocketSession? = playerSessions[playerId]
    fun getDisconnectedPlayer(playerId: String): DisconnectedPlayer? = disconnectedPlayers[playerId]
    fun isDisconnected(playerId: String): Boolean = disconnectedPlayers.containsKey(playerId)
} 