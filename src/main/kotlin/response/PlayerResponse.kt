package response

import kotlinx.serialization.Serializable
import model.Player

/**
 * @author guvencenanguvenal
 */
@Serializable
data class PlayerResponse(val id: String, val name: String, val avatarUrl: String) {
    constructor(player: Player) : this(player.id, player.name, player.avatarUrl)
}
