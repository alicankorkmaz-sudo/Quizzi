package model

import data.QuestionDatabase

/**
 * @author guvencenanguvenal
 */
class ResistanceGame(
    id: String,
    players: MutableList<Player> = mutableListOf(),
    currentQuestion: Question? = null,
    var cursorPosition: Float = 0.5f
) : Game(id, players, currentQuestion) {

    private val ROUND_TIME_SECONDS = 10L

    private val MAX_PLAYERS = 2
    override fun nextQuestion(): Question {
        currentQuestion = QuestionDatabase.getRandomQuestion()
        return currentQuestion!!
    }

    override fun processAnswer(answeredPlayerId: String?, answer: Int?) {
        val correctAnswer = answer == currentQuestion?.answer

        if (correctAnswer) {
            val correctPlayer = players.find { p ->
                p.id == answeredPlayerId
            }

            if (correctPlayer != null) {
                val currentPosition = cursorPosition
                val movement = if (players.indexOf(correctPlayer) == 0) -0.1f else 0.1f
                val newPosition = currentPosition + movement
                cursorPosition = when {
                    newPosition <= 0.1f -> 0f  // Sol limit
                    newPosition >= 0.9f -> 1f  // Sağ limit
                    else -> newPosition
                }
            }
        }
    }

    override fun getRoundTime(): Long {
        return ROUND_TIME_SECONDS
    }

    override fun maxPlayerCount(): Int {
        return MAX_PLAYERS
    }
}