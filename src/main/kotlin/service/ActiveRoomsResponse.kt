package service

import kotlinx.serialization.Serializable

@Serializable
data class ActiveRoomsResponse(
    val rooms: List<ActiveRoom>
) 