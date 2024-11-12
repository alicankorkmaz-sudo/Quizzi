package response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import model.ClientQuestion
import model.Player
import model.RoomState

/**
 * @author guvencenanguvenal
 */
@Serializable
@JsonClassDiscriminator("type")
sealed class ServerSocketMessage() {

    @Serializable
    @SerialName("RoomCreated")
    data class RoomCreated(
        val roomId: String
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("JoinedRoom")
    data class JoinedRoom(
        val roomId: String,
        val success: Boolean
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("RoomUpdate")
    data class RoomUpdate(
        val players: List<Player>,
        val state: RoomState,
        val cursorPosition: Float,
        val timeRemaining: Long? = null,
        val currentQuestion: ClientQuestion? = null
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("TimeUpdate")
    data class TimeUpdate(
        val remaining: Long
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("TimeUp")
    data class TimeUp(
        val correctAnswer: Int
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("GameOver")
    data class GameOver(
        val winnerPlayerId: String
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("AnswerResult")
    data class AnswerResult(
        val playerId: String,
        val answer: Int,
        val correct: Boolean
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("RoundResult")
    data class RoundResult(
        val correctAnswer: Int,
        val winnerPlayerId: String?
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("PlayerDisconnected")
    data class PlayerDisconnected(
        val playerId: String,
        val playerName: String
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("PlayerReconnected")
    data class PlayerReconnected(
        val playerId: String
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("RoomClosed")
    data class RoomClosed(
        val reason: String
    ) : ServerSocketMessage()
}
