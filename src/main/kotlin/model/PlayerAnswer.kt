package model

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
data class PlayerAnswer(val playerId: String, val answer: Int)
