package model

import enums.RoundState
import exception.AlreadyAnswered
import exception.WrongCommandWrongTime
import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable
import java.util.*

/**
 * @author guvencenanguvenal
 */
@Serializable
data class Round(
    val number: Int,
    val question: Question,
    var job: Job? = null,
    val playerAnswers: MutableList<PlayerAnswer> = Collections.synchronizedList(mutableListOf())
) {
    private var state: RoundState = RoundState.Idle

    fun transitionTo(newState: RoundState) {
        when(state) {
            is RoundState.Interrupt -> {
                if (newState is RoundState.Start) {
                    throw IllegalStateException("Invalid transition from Start to $newState")
                }
            }
            else -> {}
        }
        state = newState
        onChanged(newState)
    }

    private fun onChanged(newState: RoundState) {
        when(newState) {
            RoundState.Start -> {
                job.start()
            }
            is RoundState.Interrupt -> {
                playerAnswered(newState.player, newState.answer)
                job.cancel()
            }
            RoundState.End -> return
            RoundState.Idle -> return
        }
    }

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
        if (playerAnswers.any { playerAnswer -> playerAnswer.correct }) {
            return true
        }

        //iki kullanici da bilemedi
        if (playerAnswers.size == playerCount && playerAnswers.none { playerAnswer -> playerAnswer.correct }) {
            return true
        }

        return false
    }
}