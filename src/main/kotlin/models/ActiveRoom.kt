package models

import kotlinx.serialization.Serializable

@Serializable
data class ActiveRoom(
    val id: String,
    val playerCount: Int,
    val gameState: GameState,
    val players: List<String>
)