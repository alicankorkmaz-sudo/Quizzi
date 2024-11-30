package model

import data.QuestionDatabase

/**
 * @author guvencenanguvenal
 */
class ResistanceGame(
    id: String,
    categoryId: Int,
    rounds: MutableList<Round> = mutableListOf(),
    var cursorPosition: Float = 0.5f
) : Game(id, categoryId, rounds) {

    companion object {
        private const val ROUND_TIME_SECONDS = 10L
        private const val MAX_PLAYERS = 2
    }

    override fun nextQuestion(): Question {
        return QuestionDatabase.getRandomQuestion(categoryId)
    }

    override fun processAnswer(answeredPlayer: PlayerInRoom, answer: Int) {
        val round = getLastRound()
        val isCorrect = answer == getLastRound().question.answer

        //hali hazirda kazanan varsa ikinci yaniti handle etme
        if (round.winnerPlayer != null) {
            return
        }

        round.playerAnswers.add(PlayerAnswer(answeredPlayer.id, answer))
        if (isCorrect) {
            round.winnerPlayer = answeredPlayer.toPlayer()

            val currentPosition = cursorPosition
            val movement = if (answeredPlayer.index == 0) -0.1f else 0.1f
            val newPosition = currentPosition + movement
            cursorPosition = when {
                newPosition <= 0.1f -> 0f  // Sol limit
                newPosition >= 0.9f -> 1f  // Sağ limit
                else -> newPosition
            }
        }
    }

    override fun getRoundTime(): Long {
        return ROUND_TIME_SECONDS
    }

    override fun maxPlayerCount(): Int {
        return MAX_PLAYERS
    }

    override fun nextRound(): Round {
        val roundNumber = rounds.size + 1
        val round = Round(roundNumber, nextQuestion())
        rounds.add(round)
        return round
    }

    override fun getLastRound(): Round {
        return rounds.last()
    }

    override fun isGameOver(): Boolean {
        return cursorPosition <= 0f || cursorPosition >= 1f
    }
}