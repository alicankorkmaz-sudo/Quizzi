package response

import kotlinx.serialization.Serializable
import dto.GameRoomDTO

@Serializable
data class ActiveRoomsResponse(
    val rooms: List<GameRoomDTO>
)