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
    val playerCount: Int,
    var job: Job? = null,
    val playerAnswers: MutableList<PlayerAnswer> = Collections.synchronizedList(mutableListOf())
) {

    private var state: RoundState = RoundState.Start

    fun getState(): RoundState = state

    suspend fun transitionTo(newState: RoundState) {
        when (state) {
            RoundState.Start -> {}
            is RoundState.Answered -> {}
            RoundState.End -> {
                if (newState is RoundState.Answered) {
                    throw IllegalStateException("Invalid transition from End to $newState")
                }
            }
            RoundState.Interrupt -> {
                if (newState is RoundState.End) {
                    throw IllegalStateException("Invalid transition from Interrupt to $newState")
                }
            }
        }
        state = newState
        onStateChanged(newState)
    }

    private suspend fun onStateChanged(newState: RoundState) {
        when (newState) {
            RoundState.Start -> {}
            is RoundState.Answered -> {
                playerAnswered(newState.player, newState.answer)
            }
            RoundState.End -> job?.join()
            RoundState.Interrupt -> job?.cancel()
        }
    }

    private suspend fun playerAnswered(player: Player, answer: Int) {
        synchronized(playerAnswers) {
            // round bittiyse gec gelen cevabi handle etme
            if (job?.isActive != true) {
                throw WrongCommandWrongTime()
            }

            //hali hazirda dogru yanit varsa ikinci yaniti handle etme
            if (hasCorrectAnswer()) {
                throw AlreadyAnswered()
            }
            playerAnswers.add(PlayerAnswer(player, answer, question.answer == answer))
        }

        if (isRoundOver()) {
            transitionTo(RoundState.Interrupt)
        }
    }

    private fun isRoundOver(): Boolean {
        return hasCorrectAnswer() || (isAllPlayersAnswered(playerCount) && !hasCorrectAnswer())
    }

    ////////////////////////

    fun roundWinnerPlayer(): Player? {
        return playerAnswers.find { it.correct }?.player
    }


    private fun hasCorrectAnswer(): Boolean {
        return playerAnswers.any { it.correct }
    }

    private fun isAllPlayersAnswered(playerCount: Int): Boolean {
        return playerAnswers.size == playerCount
    }
}