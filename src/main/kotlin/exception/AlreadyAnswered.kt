package exception

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
class AlreadyAnswered : BusinessError("Question has been already answered!")