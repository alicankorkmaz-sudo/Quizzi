package state

import kotlinx.serialization.Serializable
import model.Player

@Serializable
sealed class RoundState {
    @Serializable
    data object Start : RoundState()
    @Serializable
    data object Interrupt : RoundState()
    @Serializable
    data object End : RoundState()
}