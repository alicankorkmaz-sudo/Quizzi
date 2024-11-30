package model

import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
data class Round(
    val number: Int,
    val question: Question,
    var job: Job? = null,
    var answer: Int? = null,
    val playerAnswers: MutableList<PlayerAnswer> = mutableListOf(),
    var winnerPlayer: Player? = null
)