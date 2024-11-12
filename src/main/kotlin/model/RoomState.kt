package model

import kotlinx.serialization.Serializable

@Serializable
enum class RoomState {
    WAITING,
    COUNTDOWN,
    PLAYING,
    PAUSED,    // Yeni durum
    ROUND_END,
    FINISHED
}