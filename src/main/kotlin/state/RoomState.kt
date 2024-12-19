package state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RoomState {
    @Serializable
    @SerialName("Waiting")
    data object Waiting : RoomState()

    @Serializable
    @SerialName("Countdown")
    data object Countdown : RoomState()

    @Serializable
    @SerialName("Pausing")
    data object Pausing : RoomState()

    @Serializable
    @SerialName("Playing")
    data object Playing : RoomState()

    @Serializable
    @SerialName("Closing")
    data object Closing : RoomState()
}