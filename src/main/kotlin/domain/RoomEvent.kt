package domain

import kotlinx.serialization.Serializable
import model.Player

/**
 * @author guvencenanguvenal
 */
@Serializable
sealed class RoomEvent {

    @Serializable
    data class Created(val player: Player) : RoomEvent()

    @Serializable
    data class Joined(val player: Player) : RoomEvent()

    @Serializable
    data object Rejoined : RoomEvent()

    @Serializable
    data class Ready(val playerId: String) : RoomEvent()

    @Serializable
    data class Disconnected(val playerId: String) : RoomEvent()
}