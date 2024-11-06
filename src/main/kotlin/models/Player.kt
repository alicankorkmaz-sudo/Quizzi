package models

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: String,
    val name: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Player) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}