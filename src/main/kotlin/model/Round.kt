package model

import dto.PlayerDTO
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
    var answeredPlayer: PlayerDTO? = null
)