package model

import kotlinx.serialization.Serializable

// Server tarafında kullanılacak tam soru modeli
@Serializable
data class Question(
    val flagId: String,
    val flagUrl: String,
    val options: List<Option>,
    val correctAnswer: String
)

@Serializable
data class Option(
    val id: String,
    val name: String
)

// Client'a gönderilecek güvenli versiyon
@Serializable
data class ClientQuestion(
    val flagUrl: String,
    val options: List<Option>
)

// Extension function for easy conversion
fun Question.toClientQuestion() = ClientQuestion(
    flagUrl = flagUrl,
    options = options
) 