package enums

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
sealed class RoomState {

    @Serializable
    object Waiting : RoomState()

    @Serializable
    object Countdown : RoomState()

    @Serializable
    object Playing : RoomState()

    @Serializable
    object Paused : RoomState()

    @Serializable
    object Closed : RoomState()
}