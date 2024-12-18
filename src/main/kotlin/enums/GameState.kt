package enums

import kotlinx.serialization.Serializable
import model.Player

@Serializable
sealed class GameState {
    @Serializable
    data object Start : GameState()
    @Serializable
    data object RoundStart : GameState()
    @Serializable
    data class RoundAnswered(val player: Player, val answer: Int) : GameState()
    @Serializable
    data object RoundEnd : GameState()
    @Serializable
    data object Over : GameState()
}