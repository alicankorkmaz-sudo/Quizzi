package exception

import kotlinx.serialization.Serializable

@Serializable
data class PlayerSessionNotFound(val playerId: String) : SocketCloseError("Player Session Not Found! PlayerId: $playerId")