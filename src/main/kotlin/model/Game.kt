package model

import domain.GameEvent
import kotlinx.serialization.Serializable
import state.GameState
import java.util.*

/**
 * @author guvencenanguvenal
 */
@Serializable
abstract class Game(
    val id: String,
    val whichRoomInIt: String,
    val categoryId: Int,
    val players: MutableList<PlayerInGame> = Collections.synchronizedList(mutableListOf()),
    val rounds: MutableList<Round> = Collections.synchronizedList(mutableListOf())
) {

    abstract suspend fun transitionTo(newState: GameState)

    abstract suspend fun handleEvent(event: GameEvent)

    abstract fun calculateResult()

    abstract fun maxPlayerCount(): Int

    abstract fun getRoundTime(): Long

    abstract suspend fun nextRound(): Round

    abstract fun getLastRound(): Round
}