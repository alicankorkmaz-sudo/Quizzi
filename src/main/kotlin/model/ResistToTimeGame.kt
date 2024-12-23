package model

import data.QuestionDatabase
import domain.GameEvent
import domain.RoundEvent
import exception.WrongCommandWrongTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import response.ServerSocketMessage
import service.RoomBroadcastService
import state.GameState
import state.RoundState
import kotlin.coroutines.cancellation.CancellationException

/**
 * @author guvencenanguvenal
 */
class ResistToTimeGame(
    id: String,
    categoryId: Int,
    whichRoomInIt: String,
    players: MutableSet<PlayerInGame> = mutableSetOf(),
    rounds: MutableList<Round> = mutableListOf(),
    private var cursorPosition: Float = 0.5f
) : Game(id, whichRoomInIt, categoryId, players, rounds) {

    companion object {
        private const val ROUND_TIME_SECONDS = 3L
        private const val MAX_PLAYERS = 1
    }

    private var state: GameState = GameState.Idle

    override fun getState(): GameState = state

    override suspend fun transitionTo(newState: GameState) {
        if (state == newState) {
            return
        }

        when (state) {
            GameState.Idle -> {
                if (newState is GameState.Over) {
                    throw IllegalStateException("Invalid transition from Idle to $newState")
                }
            }

            GameState.Playing -> {
                if (newState is GameState.Idle) {
                    throw IllegalStateException("Invalid transition from Playing to $newState")
                }
            }

            GameState.Pause -> {}
            GameState.Over -> {
                if (newState !is GameState.Over) {
                    throw IllegalStateException("Invalid transition from Over to $newState")
                }
            }
        }
        state = newState
        onStateChanged(newState)
    }

    override suspend fun handleEvent(event: GameEvent) {
        when (state) {
            GameState.Idle -> {
                throw WrongCommandWrongTime()
            }

            GameState.Playing -> {}
            GameState.Pause -> {
                if (event is GameEvent.RoundStarted) {
                    throw WrongCommandWrongTime()
                }
            }

            GameState.Over -> {
                throw WrongCommandWrongTime()
            }

        }
        onProcessEvent(event)
    }

    private suspend fun onStateChanged(newState: GameState) {
        when (newState) {
            GameState.Idle -> {}
            GameState.Playing -> {
                handleEvent(GameEvent.RoundStarted)
            }

            GameState.Pause -> {
                getLastRound().transitionTo(RoundState.Interrupt)
                //rounds.removeAt(rounds.size - 1) TODO gerek yok gibi
            }

            GameState.Over -> {
                rounds.forEach { round -> round.job?.cancel() }
                broadcast(ServerSocketMessage.GameOver(winnerPlayerId = getLastRound().roundWinnerPlayer()?.id))
            }
        }
    }

    private suspend fun onProcessEvent(event: GameEvent) {
        when (event) {
            GameEvent.RoundStarted -> {
                if (gameOver()) {
                    transitionTo(GameState.Over)
                    return
                }

                val round = nextRound()
                val roundStarted = ServerSocketMessage.RoundStarted(
                    roundNumber = round.number,
                    timeRemaining = getRoundTime(),
                    currentQuestion = getLastRound().question.toDTO()
                )
                broadcast(roundStarted)
                round.job = CoroutineScope(Dispatchers.Default).launch {
                    try {
                        for (timeLeft in getRoundTime() - 1 downTo 1) {
                            delay(1000)
                            val timeUpdate = ServerSocketMessage.TimeUpdate(remaining = timeLeft)
                            broadcast(timeUpdate)
                        }
                        delay(1000)
                        // Süre doldu mesajı
                        val timeUpMessage = ServerSocketMessage.TimeUp(correctAnswer = getLastRound().question.answer)
                        broadcast(timeUpMessage)
                    } catch (e: CancellationException) {
                        // Timer iptal edildi
                    }
                }
                round.job?.invokeOnCompletion {
                    CoroutineScope(Dispatchers.Default).launch {
                        handleEvent(GameEvent.RoundEnded)
                    }
                }
            }

            is GameEvent.RoundAnswered -> {
                val lastRound = getLastRound()
                lastRound.handleEvent(RoundEvent.Answered(event.player, event.answer))

                val answerResult = ServerSocketMessage.AnswerResult(
                    playerId = event.player.id,
                    answer = event.answer,
                    correct = lastRound.question.answer == event.answer
                )
                broadcast(answerResult)
            }

            GameEvent.RoundEnded -> {
                calculateResult()
                val lastRound = getLastRound()
                val roundEnded = ServerSocketMessage.RoundEnded(
                    cursorPosition = cursorPosition,
                    correctAnswer = lastRound.question.answer,
                    winnerPlayerId = lastRound.roundWinnerPlayer()?.id
                )
                broadcast(roundEnded)
                lastRound.transitionTo(RoundState.End)
                handleEvent(GameEvent.RoundStarted)
            }
        }
    }

    override fun calculateResult() {
        val lastRound = getLastRound()

        val roundWinnerPlayer = lastRound.roundWinnerPlayer()

        if (roundWinnerPlayer != null) {
            val currentPosition = cursorPosition
            val movement = -0.1f
            val newPosition = currentPosition + movement
            cursorPosition = when {
                newPosition <= 0.1f -> 0f  // Sol limit
                newPosition >= 0.9f -> 1f  // Sağ limit
                else -> newPosition
            }
        } else {
            val currentPosition = cursorPosition
            val movement = 0.1f
            val newPosition = currentPosition + movement
            cursorPosition = when {
                newPosition <= 0.1f -> 0f  // Sol limit
                newPosition >= 0.9f -> 1f  // Sağ limit
                else -> newPosition
            }
        }
    }

    override fun maxPlayerCount(): Int {
        return MAX_PLAYERS
    }

    override fun getRoundTime(): Long {
        return ROUND_TIME_SECONDS
    }

    override fun getLastRound(): Round {
        return rounds.last()
    }

    /////////////////////////////

    private fun gameOver(): Boolean {
        return cursorPosition <= 0f || cursorPosition >= 1f
    }

    private suspend fun nextRound(): Round {
        val roundNumber = rounds.size + 1
        val round = Round(roundNumber, nextQuestion(), MAX_PLAYERS)
        round.transitionTo(RoundState.Start)
        rounds.add(round)
        return round
    }

    private fun nextQuestion(): Question {
        var randomQuestion = QuestionDatabase.getRandomQuestion(categoryId)

        while (rounds.any { r -> r.question == randomQuestion }) {
            randomQuestion = QuestionDatabase.getRandomQuestion(categoryId)
        }

        return randomQuestion
    }

    private suspend fun broadcast(message: ServerSocketMessage) {
        RoomBroadcastService.INSTANCE.broadcast(whichRoomInIt, message)
    }
}