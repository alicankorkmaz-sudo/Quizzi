package exception

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
open class BusinessError(override val message: String) : Exception(message)
