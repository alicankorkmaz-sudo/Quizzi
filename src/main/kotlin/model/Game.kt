package model

import dto.PlayerDTO
import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
abstract class Game(
    val id: String,
    val categoryId: Int,
    val rounds: MutableList<Round> = mutableListOf()
) {
    abstract fun gameOver(): Boolean

    abstract fun calculateResult(players: MutableList<PlayerDTO>)

    abstract fun maxPlayerCount(): Int

    abstract fun getRoundTime(): Long

    abstract fun nextRound(): Round

    abstract fun getLastRound(): Round
}