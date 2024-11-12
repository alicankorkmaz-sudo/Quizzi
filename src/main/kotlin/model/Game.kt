package model

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
abstract class Game (
    val id: String,
    var currentQuestion: Question? = null
) {
    abstract fun nextQuestion() : Question
    abstract fun processAnswer(players: MutableList<Player>, answeredPlayerId: String?, answer: Int?): Boolean

    abstract fun getRoundTime() : Long

    abstract fun maxPlayerCount() : Int
}