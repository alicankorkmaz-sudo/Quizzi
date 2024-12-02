package model

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
data class PlayerAnswer(val player: Player, val answer: Int, val correct: Boolean)
