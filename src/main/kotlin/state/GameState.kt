package state

import kotlinx.serialization.Serializable

@Serializable
sealed class GameState {
    @Serializable
    data object Idle : GameState()

    @Serializable
    data object Playing : GameState()

    @Serializable
    data object Over : GameState()
}