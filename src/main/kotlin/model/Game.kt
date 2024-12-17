package model

import dto.PlayerDTO
import kotlinx.coroutines.Job
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
    abstract fun gameOver(): Boolean

    abstract fun calculateResult(players: MutableList<PlayerDTO>)

    abstract fun maxPlayerCount(): Int

    abstract fun getRoundTime(): Long

    abstract fun nextRound(): Round

    abstract fun getLastRound(): Round
}