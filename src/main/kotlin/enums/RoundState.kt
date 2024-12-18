package enums

import kotlinx.serialization.Serializable
import model.Player

@Serializable
sealed class RoundState {
    @Serializable
    data object Start : RoundState()
    @Serializable
    data class Answered(val player: Player, val answer: Int) : RoundState()
    @Serializable
    data object Interrupt : RoundState()
    @Serializable
    data object End : RoundState()
}