package exception

import kotlinx.serialization.Serializable

@Serializable
data class RoomNotFound(val roomId: String) : SocketCloseError("Room Not Found! RoomId: $roomId")