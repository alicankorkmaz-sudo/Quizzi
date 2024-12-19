package domain

import kotlinx.serialization.Serializable
import model.Player

/**
 * @author guvencenanguvenal
 */
@Serializable
sealed class GameEvent {
    @Serializable
    data object RoundStarted : GameEvent()

    @Serializable
    data class RoundAnswered(val player: Player, val answer: Int) : GameEvent()

    @Serializable
    data object RoundEnded : GameEvent()
}