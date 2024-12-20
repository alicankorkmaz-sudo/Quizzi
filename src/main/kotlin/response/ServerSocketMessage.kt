package response

import dto.PlayerDTO
import dto.QuestionDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import state.RoomState

/**
 * @author guvencenanguvenal
 */
@Serializable
@JsonClassDiscriminator("type")
sealed class ServerSocketMessage {

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
    @SerialName("RejoinedRoom")
    data class RejoinedRoom(
        val roomId: String,
        val playerId: String,
        val success: Boolean
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("CountdownTimeUpdate")
    data class CountdownTimeUpdate(
        val remaining: Long
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("RoomUpdate")
    data class RoomUpdate(
        val players: List<PlayerDTO>,
        val state: RoomState,
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
        val winnerPlayerId: String?
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("AnswerResult")
    data class AnswerResult(
        val playerId: String,
        val answer: Int,
        val correct: Boolean
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("RoundStarted")
    data class RoundStarted(
        val roundNumber: Int,
        val timeRemaining: Long,
        val currentQuestion: QuestionDTO
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("RoundEnded")
    data class RoundEnded(
        val cursorPosition: Float,
        val correctAnswer: Int,
        val winnerPlayerId: String?
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("PlayerDisconnected")
    data class PlayerDisconnected(
        val playerId: String
    ) : ServerSocketMessage()

    @Serializable
    @SerialName("RoomClosed")
    data class RoomClosed(
        val reason: String
    ) : ServerSocketMessage()
}
