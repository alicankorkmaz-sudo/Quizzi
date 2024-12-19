package model

import data.QuestionDatabase
import domain.GameEvent
import domain.RoundEvent
import dto.PlayerDTO
import kotlinx.coroutines.*
import response.ServerSocketMessage
import service.RoomBroadcastService
import state.GameState
import state.RoundState
import kotlin.coroutines.cancellation.CancellationException

/**
 * @author guvencenanguvenal
 */
class ResistanceGame(
    id: String,
    categoryId: Int,
    private val roomId: String,
    rounds: MutableList<Round> = mutableListOf(),
    var cursorPosition: Float = 0.5f
) : Game(id, categoryId, rounds) {

    companion object {
        private const val ROUND_TIME_SECONDS = 10L
        private const val MAX_PLAYERS = 2
    }

    private var state: GameState = GameState.Idle

    override suspend fun transitionTo(newState: GameState) {
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

            GameState.Over -> {
                if (newState !is GameState.Over) {
                    throw IllegalStateException("Invalid transition from Idle to $newState")
                }
            }
        }
        state = newState
        onStateChanged(newState)
    }

    override suspend fun handleEvent(event: GameEvent) {
        when (state) {
            GameState.Idle -> {
                throw IllegalStateException("Invalid event to $state")
            }

            GameState.Playing -> {}
            GameState.Over -> {
                throw IllegalStateException("Invalid event to $state")
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

            GameState.Over -> {
                rounds.forEach { round -> round.job?.cancel() }
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
                RoomBroadcastService.INSTANCE.broadcast(roomId, roundStarted)
                round.job = CoroutineScope(Dispatchers.Default).launch {
                    try {
                        for (timeLeft in getRoundTime() - 1 downTo 1) {
                            delay(1000)
                            val timeUpdate = ServerSocketMessage.TimeUpdate(remaining = timeLeft)
                            RoomBroadcastService.INSTANCE.broadcast(roomId, timeUpdate)
                        }
                        delay(1000)
                        // Süre doldu mesajı
                        val timeUpMessage = ServerSocketMessage.TimeUp(correctAnswer = getLastRound().question.answer)
                        RoomBroadcastService.INSTANCE.broadcast(roomId, timeUpMessage)
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
                RoomBroadcastService.INSTANCE.broadcast(roomId, answerResult)
            }

            GameEvent.RoundEnded -> {
                val roundEnded = ServerSocketMessage.RoundEnded(
                    cursorPosition = cursorPosition,
                    correctAnswer = getLastRound().question.answer,
                    winnerPlayerId = null
                )
                RoomBroadcastService.INSTANCE.broadcast(roomId, roundEnded)
                getLastRound().transitionTo(RoundState.End)
                handleEvent(GameEvent.RoundStarted)
            }
        }
    }

    /////////////////////////////

    private fun gameOver(): Boolean {
        return cursorPosition <= 0f || cursorPosition >= 1f
    }

    private fun nextQuestion(): Question {
        var randomQuestion = QuestionDatabase.getRandomQuestion(categoryId)

        while (rounds.any { r -> r.question == randomQuestion }) {
            randomQuestion = QuestionDatabase.getRandomQuestion(categoryId)
        }

        return randomQuestion
    }

    /////////////////////////////

    override fun calculateResult(players: MutableList<PlayerDTO>) {
        val lastRound = getLastRound()

        val roundWinnerPlayer = lastRound.roundWinnerPlayer()

        if (roundWinnerPlayer != null) {
            val currentPosition = cursorPosition
            val movement = if (players.indexOf(roundWinnerPlayer.toDTO()) == 0) -0.1f else 0.1f
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

    override suspend fun nextRound(): Round {
        val roundNumber = rounds.size + 1
        val round = Round(roundNumber, nextQuestion(), MAX_PLAYERS)
        round.transitionTo(RoundState.Start)
        rounds.add(round)
        return round
    }

    override fun getLastRound(): Round {
        return rounds.last()
    }
}