package model

import kotlinx.serialization.Serializable

@Serializable
data class Player(val id: String, val name: String, val avatarUrl: String)
