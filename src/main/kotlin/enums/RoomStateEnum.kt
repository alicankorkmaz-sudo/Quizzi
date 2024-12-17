package enums

import kotlinx.serialization.Serializable

@Serializable
enum class RoomStateEnum {
    WAITING,
    COUNTDOWN,
    PLAYING,
    PAUSED,
    CLOSED
}