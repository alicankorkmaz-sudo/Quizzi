package data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import model.Question

object QuestionDatabase {
    private val questions: List<Question> = loadFlags()

    private fun loadFlags(): List<Question> {
        val inputStream = javaClass.getResourceAsStream("/flagQuestions.json")
        return inputStream?.bufferedReader()?.use { reader ->
            Json.decodeFromString<QuestionResponse>(reader.readText()).questions
        } ?: emptyList()
    }

    fun getRandomQuestion(categoryId: Int): Question {
        val question = questions.filter { q -> q.categoryId == categoryId }.shuffled().first()

        return Question(
            id = question.id,
            categoryId = question.categoryId,
            imageUrl = question.imageUrl,
            content = question.content,
            options = question.options.shuffled(),
            answer = question.answer
        )
    }

    @Serializable
    private data class QuestionResponse(
        val questions: List<Question>
    )
}