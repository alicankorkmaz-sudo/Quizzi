package data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import model.Flag
import model.Option
import model.Question

object FlagDatabase {
    private val flags: List<Flag> = loadFlags()

    private fun loadFlags(): List<Flag> {
        val inputStream = javaClass.getResourceAsStream("/flags.json")
        return inputStream?.bufferedReader()?.use { reader ->
            Json.decodeFromString<FlagResponse>(reader.readText()).flags
        } ?: emptyList()
    }

    fun getRandomFlags(count: Int): List<Flag> {
        return flags.shuffled().take(count)
    }

    fun getRandomQuestion(): Question {
        val allFlags = flags.shuffled()
        val correctFlag = allFlags.first()
        val options = allFlags.take(4).map { Option(it.id, it.name.tr) }.shuffled()

        return Question(
            flagId = correctFlag.id,
            flagUrl = correctFlag.flagUrl,
            options = options,
            correctAnswer = correctFlag.id
        )
    }

    @Serializable
    private data class FlagResponse(
        val flags: List<Flag>
    )
}