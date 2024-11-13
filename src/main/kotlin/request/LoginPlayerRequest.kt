package request

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
data class LoginPlayerRequest(val id: String)
