package model

import domain.RoundEvent
import exception.AlreadyAnswered
import exception.WrongCommandWrongTime
import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable
import state.RoundState
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
    val playerAnswers: MutableSet<PlayerAnswer> = Collections.synchronizedSet(mutableSetOf())
) {

    private var state: RoundState = RoundState.Start

    suspend fun transitionTo(newState: RoundState) {
        when (state) {
            RoundState.Start -> {}
            RoundState.Interrupt -> {}
            RoundState.End -> {
                if (newState is RoundState.Start) {
                    throw IllegalStateException("Invalid transition from End to $newState")
                }
            }
        }
        state = newState
        onStateChanged(newState)
    }

    suspend fun handleEvent(event: RoundEvent) {
        when (state) {
            RoundState.Start -> {}
            RoundState.Interrupt -> {
                if (event is RoundEvent.Answered) {
                    throw WrongCommandWrongTime()
                }
            }

            RoundState.End -> {
                if (event is RoundEvent.Answered) {
                    throw WrongCommandWrongTime()
                }
            }
        }
        onProcessEvent(event)
    }

    private suspend fun onStateChanged(newState: RoundState) {
        when (newState) {
            RoundState.Start -> {}
            RoundState.Interrupt -> {
                job?.cancel()
                transitionTo(RoundState.End)
            }

            RoundState.End -> {
                if (job != null && job!!.isActive) {
                    job?.join()
                }
            }
        }
    }

    private suspend fun onProcessEvent(event: RoundEvent) {
        when (event) {
            is RoundEvent.Answered -> {
                playerAnswered(event.player, event.answer)
                if (isRoundOver()) {
                    transitionTo(RoundState.Interrupt)
                }
            }
        }
    }

    ///////////////////////////////

    private fun playerAnswered(player: Player, answer: Int) {
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
    }

    private fun isRoundOver(): Boolean {
        return hasCorrectAnswer() || (isAllPlayersAnswered(playerCount) && !hasCorrectAnswer())
    }

    private fun hasCorrectAnswer(): Boolean {
        return playerAnswers.any { it.correct }
    }

    private fun isAllPlayersAnswered(playerCount: Int): Boolean {
        return playerAnswers.size == playerCount
    }

    ////////////////////////
    ////PUBLIC

    fun roundWinnerPlayer(): Player? {
        return playerAnswers.find { it.correct }?.player
    }
}