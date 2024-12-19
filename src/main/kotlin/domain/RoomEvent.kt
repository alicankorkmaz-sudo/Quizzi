package domain

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
sealed class RoomEvent {
    @Serializable
    data object Joined : RoomEvent()

    @Serializable
    data object Rejoined : RoomEvent()

    @Serializable
    data object Ready : RoomEvent()

    @Serializable
    data object Disconnected : RoomEvent()
}