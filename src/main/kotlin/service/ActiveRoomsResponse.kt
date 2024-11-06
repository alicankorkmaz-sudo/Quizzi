package service

import kotlinx.serialization.Serializable
import models.ActiveRoom

@Serializable
data class ActiveRoomsResponse(
    val rooms: List<ActiveRoom>
) 