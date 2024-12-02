package model

import dto.PlayerDTO
import exception.AlreadyAnswered
import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
data class Round(
    val number: Int,
    val question: Question,
    var job: Job? = null,
    val playerAnswers: MutableList<PlayerAnswer> = mutableListOf()
) {
    fun playerAnswered(player: Player, answer: Int) {
        //hali hazirda dogru yanit varsa ikinci yaniti handle etme
        if (playerAnswers.filter { playerAnswer -> playerAnswer.correct }.size == 1) {
            throw AlreadyAnswered()
        }
        playerAnswers.add(PlayerAnswer(player, answer, question.answer == answer))
    }

    fun roundWinnerPlayer(): Player? {
        return try {
            playerAnswers.first { playerAnswer -> playerAnswer.correct }.player
        } catch (_: NoSuchElementException) {
            null
        }
    }

    fun isRoundOver(playerCount: Int): Boolean {
        //bir adet dogru yanit var
        if (playerAnswers.filter { playerAnswer -> playerAnswer.correct }.size == 1) {
            return true
        }

        //iki kullanici da bilemedi
        if (playerAnswers.size == playerCount && playerAnswers.none { playerAnswer -> playerAnswer.correct }) {
            return true
        }

        return false
    }


}