package service.game

import data.FlagDatabase
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import models.Question

class GameStateManager(
    private val scope: CoroutineScope,
    private val json: Json
) {
    private val currentQuestions = mutableMapOf<String, Question>()
    private val roundAnswers = mutableMapOf<String, MutableMap<String, String>>()
    private val roundTimers = mutableMapOf<String, Job>()
    private val wrongAnswers = mutableMapOf<String, MutableSet<String>>()

    companion object {
        const val ROUND_TIME_SECONDS = 10L
        const val NEXT_QUESTION_DELAY_MS = 1500L
    }

    fun setQuestion(roomId: String, question: Question) {
        currentQuestions[roomId] = question
        roundAnswers[roomId]?.clear()
    }

    fun startNewRound(roomId: String): Question {
        val question = FlagDatabase.getRandomQuestion()
        setQuestion(roomId, question)
        wrongAnswers[roomId] = mutableSetOf()
        return question
    }

    fun recordAnswer(roomId: String, playerId: String, answer: String): Boolean {
        val answers = roundAnswers.getOrPut(roomId) { mutableMapOf() }
        if (answers.containsKey(playerId)) return false

        answers[playerId] = answer
        return true
    }

    fun recordWrongAnswer(roomId: String, playerId: String) {
        wrongAnswers.getOrPut(roomId) { mutableSetOf() }.add(playerId)
    }

    fun bothPlayersAnsweredWrong(roomId: String, playerCount: Int): Boolean {
        return wrongAnswers[roomId]?.size == playerCount
    }

    fun cancelTimer(roomId: String) {
        roundTimers[roomId]?.cancel()
        roundTimers.remove(roomId)
    }

    fun startRoundTimer(
        roomId: String,
        onTick: suspend (Long) -> Unit,
        onComplete: suspend () -> Unit
    ) {
        cancelTimer(roomId)
        roundTimers[roomId] = scope.launch {
            try {
                for (timeLeft in ROUND_TIME_SECONDS - 1 downTo 1) {
                    delay(1000)
                    onTick(timeLeft)
                }
                delay(1000)
                onComplete()
            } catch (e: CancellationException) {
                // Timer cancelled
            }
        }
    }

    fun cleanup(roomId: String) {
        currentQuestions.remove(roomId)
        roundAnswers.remove(roomId)
        wrongAnswers.remove(roomId)
        cancelTimer(roomId)
    }

    fun getAnswers(roomId: String): Map<String, String>? = roundAnswers[roomId]
    fun getCurrentQuestion(roomId: String): Question? = currentQuestions[roomId]
} 