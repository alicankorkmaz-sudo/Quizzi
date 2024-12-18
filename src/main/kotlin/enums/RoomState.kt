package enums

import kotlinx.serialization.Serializable

@Serializable
sealed class RoomState {
    @Serializable
    object Waiting : RoomState()
    @Serializable
    object Countdown : RoomState()
    @Serializable
    object Playing : RoomState()
    @Serializable
    object Closed : RoomState()
}