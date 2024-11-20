package model

import data.QuestionDatabase

/**
 * @author guvencenanguvenal
 */
class ResistanceGame(
    id: String,
    categoryId: Int,
    currentQuestion: Question? = null,
    var cursorPosition: Float = 0.5f
) : Game(id, categoryId, currentQuestion) {

    private val ROUND_TIME_SECONDS = 20L

    private val MAX_PLAYERS = 2

    override fun nextQuestion(): Question {
        currentQuestion = QuestionDatabase.getRandomQuestion(categoryId)
        return currentQuestion!!
    }

    override fun processAnswer(players: MutableList<Player>, answeredPlayerId: String?, answer: Int?) : Boolean {
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
                    newPosition >= 0.9f -> 1f  // Sağ limit
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
}