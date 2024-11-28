package enums

import kotlinx.serialization.Serializable

@Serializable
enum class RoomState {
    WAITING,
    COUNTDOWN,
    PLAYING,
    PAUSED,
    CLOSED
}