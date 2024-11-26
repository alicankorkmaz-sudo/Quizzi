package exception

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
open class SocketCloseError(override val message: String): Exception(message)
