package service

import io.ktor.websocket.*
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import response.ErrorMessage
import response.ServerSocketMessage
import java.lang.Exception
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

    suspend fun removePlayerSession(playerId: String) {
        playerSessions[playerId]?.close(reason = CloseReason(1,"Room closed"))
        playerSessions.remove(playerId)
    }

    suspend fun broadcastToPlayers(playerIds: MutableList<String>, message: ServerSocketMessage) {
        val sessions = Collections.synchronizedList(mutableListOf<DefaultWebSocketSession>())
        playerIds.forEach { playerId ->
            val session = playerSessions[playerId]
            if (session != null) {
                sessions.add(session)
            } else {
                println("No session found for player $playerId")
            }
        }
        sessions.forEach {
            session ->  session.send(Frame.Text(json.encodeToString(ServerSocketMessage.serializer(), message)))
        }
    }
}