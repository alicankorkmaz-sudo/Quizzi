package response

import kotlinx.serialization.Serializable
import model.Category

@Serializable
data class AllCategoriesResponse(
    val categories: Set<Category>
)