package request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * @author guvencenanguvenal
 */
@Serializable
@JsonClassDiscriminator("type")
sealed class ClientSocketMessage {

    @Serializable
    @SerialName("CreateRoom")
    data class CreateRoom(
        val categoryId: Int,
        val gameType: String
    ) : ClientSocketMessage()

    @Serializable
    @SerialName("JoinRoom")
    data class JoinRoom(
        val roomId: String
    ) : ClientSocketMessage()

    @Serializable
    @SerialName("RejoinRoom")
    data class RejoinRoom(
        val roomId: String
    ) : ClientSocketMessage()

    @Serializable
    @SerialName("PlayerReady")
    data object PlayerReady : ClientSocketMessage()

    @Serializable
    @SerialName("PlayerAnswer")
    data class PlayerAnswer(
        val answer: Int
    ) : ClientSocketMessage()
}