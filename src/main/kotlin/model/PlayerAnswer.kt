package model

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
data class PlayerAnswer(val player: Player, val answer: Int, val correct: Boolean) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val otherPlayerAnswer: PlayerAnswer = other as PlayerAnswer
        return otherPlayerAnswer.player == player
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
