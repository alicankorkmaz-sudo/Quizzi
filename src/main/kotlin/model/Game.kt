package model

import domain.GameEvent
import dto.PlayerDTO
import state.GameState
import kotlinx.serialization.Serializable
import java.util.*

/**
 * @author guvencenanguvenal
 */
@Serializable
abstract class Game(
    val id: String,
    val categoryId: Int,
    val rounds: MutableList<Round> = Collections.synchronizedList(mutableListOf())
) {

    abstract suspend fun transitionTo(newState: GameState)

    abstract suspend fun handleEvent(event: GameEvent)

    abstract fun calculateResult(players: MutableList<PlayerDTO>)

    abstract fun maxPlayerCount(): Int

    abstract fun getRoundTime(): Long

    abstract suspend fun nextRound(): Round

    abstract fun getLastRound(): Round
}