package service

import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import response.ServerSocketMessage
import java.util.*

/**
 * @author guvencenanguvenal
 */
class RoomBroadcastService private constructor() {
    companion object {
        val INSTANCE: RoomBroadcastService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomBroadcastService() }
    }

    private val roomSessions = Collections.synchronizedMap(mutableMapOf<String, MutableList<DefaultWebSocketSession>>())

    private val json = Json { ignoreUnknownKeys = true }

    fun subscribe(roomId: String, session: DefaultWebSocketSession) {
        roomSessions.computeIfAbsent(roomId) { mutableListOf() }.add(session)
    }

    suspend fun broadcast(roomId: String, message: ServerSocketMessage) {
        roomSessions[roomId]?.forEach { session ->
            session.send(Frame.Text(json.encodeToString(ServerSocketMessage.serializer(), message)))
        }
    }
}