package response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * @author guvencenanguvenal
 */
@Serializable
@JsonClassDiscriminator("type")
sealed class ErrorMessage(
    val errorCode: String,
    override val message: String
) : Exception(message) {
    @Serializable
    @SerialName("PlayerSessionNotFound")
    data class PlayerSessionNotFound(
        val playerId: String
    ) : ErrorMessage("00010", "Player Session Not Found!")

    @Serializable
    @SerialName("RoomNotFound")
    data class RoomNotFound(
        val roomId: String
    ) : ErrorMessage("00020", "Room Not Found!")
}