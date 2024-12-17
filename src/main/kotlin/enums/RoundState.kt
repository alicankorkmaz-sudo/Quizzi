package enums

import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable
import model.Player

/**
 * @author guvencenanguvenal
 */
@Serializable
sealed class RoundState {

    @Serializable
    object Idle: RoundState()

    @Serializable
    object Start: RoundState()

    @Serializable
    class Interrupt(val player: Player, val answer: Int) : RoundState()

    @Serializable
    object End : RoundState()
}