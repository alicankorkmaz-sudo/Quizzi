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
    abstract fun nextQuestion(): Question

    abstract fun processAnswer(answeredPlayer: PlayerInRoom, answer: Int)

    abstract fun getRoundTime(): Long

    abstract fun maxPlayerCount(): Int

    abstract fun nextRound(): Round

    abstract fun getLastRound(): Round

    abstract fun isGameOver(): Boolean
}