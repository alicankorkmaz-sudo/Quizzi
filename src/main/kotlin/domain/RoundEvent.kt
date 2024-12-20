package domain

import kotlinx.serialization.Serializable
import model.Player

/**
 * @author guvencenanguvenal
 */
@Serializable
sealed class RoundEvent {
    @Serializable
    data class Answered(val player: Player, val answer: Int) : RoundEvent()
}