package model

import dto.QuestionDTO
import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: Int,
    val categoryId: Int,
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Question

        return id == other.id
    }

    override fun hashCode(): Int {
        return id
    }
}

