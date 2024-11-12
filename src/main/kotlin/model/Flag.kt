package model

import kotlinx.serialization.Serializable

@Serializable
data class Flag(
    val id: String,
    val name: LocalizedText,
    val flagUrl: String,
    val flagUrlSmall: String
)

@Serializable
data class LocalizedText(
    val tr: String,
    val en: String
) 