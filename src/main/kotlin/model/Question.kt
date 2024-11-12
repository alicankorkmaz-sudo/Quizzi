package model

import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: Int,
    val imageUrl: String?,
    val content: String,
    val options: List<Option>,
    val answer: Int
)

@Serializable
data class ClientQuestion(
    val imageUrl: String?,
    val content: String,
    val options: List<Option>
)

// Extension function for easy conversion
fun Question.toClientQuestion() = ClientQuestion(
    imageUrl = imageUrl,
    content = content,
    options = options
) 