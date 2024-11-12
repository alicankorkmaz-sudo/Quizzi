package model

import kotlinx.serialization.Serializable

/**
 * @author guvencenanguvenal
 */
@Serializable
data class Option(
    val id: Int,
    val value: String
)
