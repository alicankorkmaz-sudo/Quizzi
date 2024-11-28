package dto

import enums.PlayerState
import kotlinx.serialization.Serializable
import model.Player

/**
 * @author guvencenanguvenal
 */
@Serializable
data class PlayerDTO(val id: String, val name: String, val avatarUrl: String, var state: PlayerState) {
    constructor(player: Player) : this(player.id, player.name, player.avatarUrl, PlayerState.WAIT)
}