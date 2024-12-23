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
    val players: MutableSet<PlayerInGame> = Collections.synchronizedSet(mutableSetOf()),
    val rounds: MutableList<Round> = Collections.synchronizedList(mutableListOf())
) {

    abstract fun getState(): GameState

    abstract suspend fun transitionTo(newState: GameState)

    abstract suspend fun handleEvent(event: GameEvent)

    abstract fun calculateResult()

    abstract fun maxPlayerCount(): Int

    abstract fun getRoundTime(): Long

    abstract fun getLastRound(): Round
}