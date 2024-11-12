package service

import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import response.ServerSocketMessage
import java.util.*

/**
 * @author guvencenanguvenal
 */
class SessionManagerService private constructor() {
    companion object {
        val INSTANCE: SessionManagerService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SessionManagerService() }
    }

    private val playerSessions = Collections.synchronizedMap(mutableMapOf<String, DefaultWebSocketSession>())

    private val json = Json { ignoreUnknownKeys = true }

    fun getPlayerSession(playerId: String) : DefaultWebSocketSession? {
        return playerSessions[playerId]
    }

    fun addPlayerToSession(playerId: String, session: DefaultWebSocketSession) {
        playerSessions[playerId] = session
    }

    fun removePlayerSession(playerId: String) {
        playerSessions.remove(playerId)
    }

    suspend fun broadcastToPlayers(playerIds: MutableList<String>, message: ServerSocketMessage) {
        playerIds.forEach { playerId ->
            val session = playerSessions[playerId]
            if (session != null) {
                try {
                    session.send(Frame.Text(json.encodeToString(ServerSocketMessage.serializer(), message)))
                    println("Message sent to player $playerId")
                } catch (e: Exception) {
                    println("Error sending message to player $playerId: ${e.message}")
                }
            } else {
                println("No session found for player $playerId")
            }
        }
    }
}