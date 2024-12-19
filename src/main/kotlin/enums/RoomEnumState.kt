package enums

import kotlinx.serialization.Serializable

@Serializable
enum class RoomEnumState {
    WAITING,
    COUNTDOWN,
    PLAYING,
    PAUSED,
    CLOSED
}