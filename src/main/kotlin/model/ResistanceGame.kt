package model

import data.QuestionDatabase
import dto.PlayerDTO

/**
 * @author guvencenanguvenal
 */
class ResistanceGame(
    id: String,
    categoryId: Int,
    rounds: MutableList<Round> = mutableListOf(),
    currentQuestion: Question? = null,
    var cursorPosition: Float = 0.8f
) : Game(id, categoryId, rounds, currentQuestion) {

    companion object {
        private const val ROUND_TIME_SECONDS = 10L
        private const val MAX_PLAYERS = 2
    }

    override fun nextQuestion(): Question {
        currentQuestion = QuestionDatabase.getRandomQuestion(categoryId)
        return currentQuestion!!
    }

    override fun processAnswer(players: MutableList<PlayerDTO>, answeredPlayerId: String?, answer: Int?): Boolean {
        val isCorrect = answer == currentQuestion?.answer

        if (isCorrect) {
            val correctPlayer = players.find { p ->
                p.id == answeredPlayerId
            }

            if (correctPlayer != null) {
                val currentPosition = cursorPosition
                val movement = if (players.indexOf(correctPlayer) == 0) -0.1f else 0.1f
                val newPosition = currentPosition + movement
                cursorPosition = when {
                    newPosition <= 0.1f -> 0f  // Sol limit
                    newPosition >= 0.9f -> 1.6f  // Sağ limit
                    else -> newPosition
                }
            }
        }
        return isCorrect
    }

    override fun getRoundTime(): Long {
        return ROUND_TIME_SECONDS
    }

    override fun maxPlayerCount(): Int {
        return MAX_PLAYERS
    }

    override fun nextRound(): Round {
        val roundNumber = rounds.size + 1
        val round = Round(roundNumber)
        rounds.add(round)
        nextQuestion()
        return round
    }

    override fun getLastRound(): Round {
        return rounds.last()
    }
}