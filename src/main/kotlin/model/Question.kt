package model

import dto.QuestionDTO
import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: Int,
    val imageUrl: String?,
    val content: String,
    val options: List<Option>,
    val answer: Int
) {
    fun toDTO() = QuestionDTO(
        imageUrl = imageUrl,
        content = content,
        options = options
    )
}

