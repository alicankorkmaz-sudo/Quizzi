package request

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
data class CreatePlayerRequest(val name: String, val avatarUrl: String)
