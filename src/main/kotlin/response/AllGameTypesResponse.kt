package response

import kotlinx.serialization.Serializable
import model.Category

@Serializable
data class AllGameTypesResponse(
    val types: Set<String>
)