package model

import data.QuestionDatabase
import dto.PlayerDTO
import enums.GameState
import enums.RoundState
import kotlinx.coroutines.*
import response.ServerSocketMessage
import service.RoomBroadcastService
import kotlin.coroutines.cancellation.CancellationException

/**
 * @author guvencenanguvenal
 */
class ResistanceGame(
    id: String,
    categoryId: Int,
    rounds: MutableList<Round> = mutableListOf(),
    var cursorPosition: Float = 0.5f,
    private val roomId: String
) : Game(id, categoryId, rounds) {

    companion object {
        private const val ROUND_TIME_SECONDS = 10L
        private const val MAX_PLAYERS = 2
    }

    private var state: GameState = GameState.Start

    override suspend fun transitionTo(newState: GameState) {
        when (state) {
            GameState.Start -> {}
            GameState.RoundStart -> {
                if (newState is GameState.Start) {
                    throw IllegalStateException("Invalid transition from Playing to $newState")
                }
            }
            is GameState.RoundAnswered -> {
                if (newState is GameState.Start) {
                    throw IllegalStateException("Invalid transition from Playing to $newState")
                }
            }
            GameState.RoundEnd -> {
                if (newState is GameState.Start) {
                    throw IllegalStateException("Invalid transition from Playing to $newState")
                }
            }
            GameState.Over -> TODO()
        }
        state = newState
        onStateChanged(newState)
    }

    private suspend fun onStateChanged(newState: GameState) {
        when (newState) {
            GameState.Start -> {
                transitionTo(GameState.RoundStart)
            }
            GameState.RoundStart -> {
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

                        transitionTo(GameState.RoundEnd)
                    } catch (e: CancellationException) {
                        // Timer iptal edildi
                    }
                }
            }
            is GameState.RoundAnswered -> {
                val lastRound = getLastRound()
                lastRound.transitionTo(RoundState.Answered(newState.player, newState.answer))

                val answerResult = ServerSocketMessage.AnswerResult(
                    playerId = newState.player.id,
                    answer = newState.answer,
                    correct = lastRound.question.answer == newState.answer
                )
                RoomBroadcastService.INSTANCE.broadcast(roomId, answerResult)

                if (lastRound.getState() == RoundState.Interrupt) {
                    transitionTo(GameState.RoundEnd)
                }
            }
            GameState.RoundEnd -> {
                val roundEnded = ServerSocketMessage.RoundEnded(
                    cursorPosition = cursorPosition,
                    correctAnswer = getLastRound().question.answer,
                    winnerPlayerId = null
                )
                RoomBroadcastService.INSTANCE.broadcast(roomId, roundEnded)
                getLastRound().transitionTo(RoundState.End)
                transitionTo(GameState.RoundStart)
            }
            GameState.Over -> TODO()
        }
    }

    private fun gameOver(): Boolean {
        return cursorPosition <= 0f || cursorPosition >= 1f
    }

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

    private fun nextQuestion(): Question {
        var randomQuestion = QuestionDatabase.getRandomQuestion(categoryId)

        while (rounds.any{ r -> r.question == randomQuestion }) {
            randomQuestion = QuestionDatabase.getRandomQuestion(categoryId)
        }

        return randomQuestion
    }
}