package model

import dto.PlayerDTO
import enums.GameState
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

    abstract fun calculateResult(players: MutableList<PlayerDTO>)

    abstract fun maxPlayerCount(): Int

    abstract fun getRoundTime(): Long

    abstract suspend fun nextRound(): Round

    abstract fun getLastRound(): Round
}