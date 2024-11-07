package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed class GameMessage {
    // 1. Oda Yönetimi
    @Serializable
    @SerialName("CreateRoom")
    data class CreateRoom(
        val playerName: String
    ) : GameMessage()

    @Serializable
    @SerialName("RoomCreated")
    data class RoomCreated(
        val roomId: String
    ) : GameMessage()

    @Serializable
    @SerialName("JoinRoom")
    data class JoinRoom(
        val roomId: String,
        val playerName: String
    ) : GameMessage()

    @Serializable
    @SerialName("JoinRoomResponse")
    data class JoinRoomResponse(
        val roomId: String,
        val success: Boolean
    ) : GameMessage()

    // 2. Oyun Akışı
    @Serializable
    @SerialName("GameUpdate")
    data class GameUpdate(
        val gameState: GameState,
        val cursorPosition: Float,
        val timeRemaining: Long? = null,
        val currentQuestion: ClientQuestion? = null
    ) : GameMessage()

    @Serializable
    @SerialName("TimeUpdate")
    data class TimeUpdate(
        val timeRemaining: Long
    ) : GameMessage()

    @Serializable
    @SerialName("TimeUp")
    data class TimeUp(
        val correctAnswer: String
    ) : GameMessage()

    // 3. Oyuncu Etkileşimleri
    @Serializable
    @SerialName("PlayerAnswer")
    data class PlayerAnswer(
        val answer: String
    ) : GameMessage()

    @Serializable
    @SerialName("AnswerResult")
    data class AnswerResult(
        val playerId: String,
        val playerName: String,
        val answer: String,
        val correct: Boolean
    ) : GameMessage()

    // 4. Oyun Sonuçları
    @Serializable
    @SerialName("GameOver")
    data class GameOver(
        val winner: String? = null,
        val reason: String? = null
    ) : GameMessage()

    // 5. Bağlantı Durumu
    @Serializable
    @SerialName("ConnectionState")
    data class ConnectionState(
        val connectionStateType: ConnectionStateType,
        val playerId: String,
        val playerName: String? = null,
        val reason: String? = null
    ) : GameMessage()

    @Serializable
    enum class ConnectionStateType {
        DISCONNECTED,
        RECONNECT_REQUEST,
        RECONNECT_SUCCESS,
        RECONNECT_FAILED
    }

    @Serializable
    @SerialName("Error")
    data class Error(
        val message: String
    ) : GameMessage()

    @Serializable
    @SerialName("RoundResult")
    data class RoundResult(
        val correctAnswer: String,
        val winnerPlayerId: String?,
        val winnerPlayerName: String?
    ) : GameMessage()
}

