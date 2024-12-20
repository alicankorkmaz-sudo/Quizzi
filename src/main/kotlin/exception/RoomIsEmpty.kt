package exception

import kotlinx.serialization.Serializable

@Serializable
data class RoomIsEmpty(val roomId: String) : SocketCloseError("Room is Empty! RoomId: $roomId")