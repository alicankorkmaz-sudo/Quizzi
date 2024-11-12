package model

import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
data class Round(
    val number: Int,
    var timer: Job? = null,
    var answer: String? = null,
    var answeredPlayer: Player? = null
)