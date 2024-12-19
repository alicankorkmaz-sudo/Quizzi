package state

import kotlinx.serialization.Serializable

@Serializable
sealed class RoomState {
    @Serializable
    data object Waiting : RoomState()

    @Serializable
    data object Countdown : RoomState()

    @Serializable
    data object Playing : RoomState()

    @Serializable
    data object Closed : RoomState()
}