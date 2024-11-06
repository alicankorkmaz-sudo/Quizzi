package service

import data.FlagDatabase
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import models.*
import models.GameMessage.ConnectionStateType
import java.util.*

class GameService {
    private val gameScope = CoroutineScope(Dispatchers.Default + Job())
    private val json = Json { ignoreUnknownKeys = true }

    // Oda yönetimi
    private val rooms = mutableMapOf<String, GameRoom>()
    private val playerToRoom = mutableMapOf<String, String>()

    // Oyun durumu
    private val currentQuestions = mutableMapOf<String, Question>()
    private val roundAnswers = mutableMapOf<String, MutableMap<String, String>>()
    private val roundTimers = mutableMapOf<String, Job>()

    // Oyuncu yönetimi
    private val playerSessions = mutableMapOf<String, DefaultWebSocketSession>()
    private val disconnectedPlayers = mutableMapOf<String, DisconnectedPlayer>()

    private companion object {
        const val ROUND_TIME_SECONDS = 10L
        const val RECONNECT_TIMEOUT_SECONDS = 30L
        const val GAME_OVER_DELAY_SECONDS = 5L
        const val NEXT_QUESTION_DELAY_MS = 1500L
        const val COUNTDOWN_SECONDS = 3L
    }

    // Oda Yönetimi
    fun createRoom(playerId: String, playerName: String): String {
        val roomId = UUID.randomUUID().toString()
        val player = Player(playerId, playerName)
        val room = GameRoom(roomId)
        room.players.add(player)
        rooms[roomId] = room
        playerToRoom[playerId] = roomId
        return roomId
    }

    fun joinRoom(playerId: String, roomId: String, playerName: String): Boolean {
        val room = rooms[roomId] ?: return false
        if (room.players.size >= 2) return false

        val player = Player(playerId, playerName)
        room.players.add(player)
        playerToRoom[playerId] = roomId
        return true
    }

    suspend fun registerPlayerSession(playerId: String, session: DefaultWebSocketSession) {
        playerSessions[playerId] = session
    }

    // Oyun Akışı
    suspend fun startGame(roomId: String) {
        val room = rooms[roomId] ?: return
        if (room.players.size != 2) return

        gameScope.launch {
            room.gameState = GameState.COUNTDOWN
            broadcastGameState(roomId)
            delay(COUNTDOWN_SECONDS * 1000)

            room.gameState = GameState.PLAYING
            nextQuestion(roomId)
        }
    }

    private suspend fun nextQuestion(roomId: String) {
        val question = FlagDatabase.getRandomQuestion()
        currentQuestions[roomId] = question
        roundAnswers[roomId]?.clear()

        val gameUpdate = GameMessage.GameUpdate(
            gameState = GameState.PLAYING,
            cursorPosition = rooms[roomId]?.cursorPosition ?: 0.5f,
            timeRemaining = ROUND_TIME_SECONDS,
            currentQuestion = question.toClientQuestion()
        )

        broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), gameUpdate))
        startRoundTimer(roomId)
    }

    private fun startRoundTimer(roomId: String) {
        roundTimers[roomId]?.cancel()
        roundTimers[roomId] = gameScope.launch {
            try {
                for (timeLeft in ROUND_TIME_SECONDS - 1 downTo 1) {
                    delay(1000)
                    broadcastToRoom(
                        roomId, json.encodeToString(
                            GameMessage.serializer(),
                            GameMessage.TimeUpdate(timeRemaining = timeLeft)
                        )
                    )
                }
                delay(1000)
                handleRoundEnd(roomId)
            } catch (e: CancellationException) {
                // Timer cancelled
            }
        }
    }

    // Mesaj İşleme
    suspend fun handleMessage(playerId: String, message: String) {
        val gameMessage = try {
            json.decodeFromString<GameMessage>(message)
        } catch (e: Exception) {
            return
        }

        when (gameMessage) {
            is GameMessage.ConnectionState -> handleConnectionState(playerId, gameMessage)
            is GameMessage.CreateRoom -> handleCreateRoom(playerId, gameMessage)
            is GameMessage.JoinRoom -> handleJoinRoom(playerId, gameMessage)
            is GameMessage.PlayerAnswer -> handlePlayerAnswer(playerId, gameMessage)
            else -> Unit
        }
    }

    private suspend fun handleCreateRoom(playerId: String, message: GameMessage.CreateRoom) {
        val roomId = createRoom(playerId, message.playerName)
        val response = GameMessage.RoomCreated(roomId = roomId)
        playerSessions[playerId]?.send(Frame.Text(json.encodeToString(GameMessage.serializer(), response)))
    }

    private suspend fun handleJoinRoom(playerId: String, message: GameMessage.JoinRoom) {
        val success = joinRoom(playerId, message.roomId, message.playerName)
        val response = GameMessage.JoinRoomResponse(message.roomId, success = success)
        playerSessions[playerId]?.send(Frame.Text(json.encodeToString(GameMessage.serializer(), response)))
        if (success) {
            startGame(message.roomId)
        }
    }

    // Bağlantı Yönetimi
    private suspend fun handleConnectionState(
        playerId: String,
        message: GameMessage.ConnectionState
    ) {
        when (message.type) {
            ConnectionStateType.RECONNECT_REQUEST -> handleReconnectRequest(message)
            ConnectionStateType.DISCONNECTED -> handleDisconnect(playerId)
            else -> Unit
        }
    }

    private suspend fun handleReconnectRequest(
        message: GameMessage.ConnectionState
    ) {
        val session = playerSessions[message.playerId] ?: return

        val success = handleReconnect(message.playerId, session)
        val responseType = if (success) {
            ConnectionStateType.RECONNECT_SUCCESS
        } else {
            ConnectionStateType.RECONNECT_FAILED
        }

        session.send(
            Frame.Text(
                json.encodeToString(
                    GameMessage.serializer(),
                    GameMessage.ConnectionState(
                        type = responseType,
                        playerId = message.playerId,
                        playerName = disconnectedPlayers[message.playerId]?.playerName
                    )
                )
            )
        )
    }

    // Yardımcı Fonksiyonlar
    private suspend fun broadcastToRoom(roomId: String, message: String) {
        val room = rooms[roomId] ?: return
        room.players.forEach { player ->
            playerSessions[player.id]?.let { session ->
                try {
                    session.send(Frame.Text(message))
                } catch (e: Exception) {
                    handleDisconnect(player.id)
                }
            }
        }
    }

    private suspend fun broadcastGameState(roomId: String) {
        val room = rooms[roomId] ?: return
        val gameUpdate = GameMessage.GameUpdate(
            gameState = room.gameState,
            cursorPosition = room.cursorPosition,
            currentQuestion = currentQuestions[roomId]?.toClientQuestion()
        )
        broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), gameUpdate))
    }

    fun getActiveRooms(): List<ActiveRoom> = rooms.map { (id, room) ->
        ActiveRoom(
            id = id,
            playerCount = room.players.size,
            gameState = room.gameState,
            players = room.players.map { it.name }
        )
    }

    private suspend fun handleRoundEnd(roomId: String) {
        val room = rooms[roomId] ?: return
        val question = currentQuestions[roomId] ?: return
        val answers = roundAnswers[roomId] ?: return

        // Süre dolduğunda cevap vermemiş oyuncuları işle
        room.players.forEach { player ->
            if (!answers.containsKey(player.id)) {
                answers[player.id] = ""
            }
        }

        // Doğru cevabı gönder
        broadcastToRoom(
            roomId,
            json.encodeToString(
                GameMessage.serializer(),
                GameMessage.TimeUp(correctAnswer = question.correctAnswer)
            )
        )

        // Cevap sonuçlarını gönder
        answers.forEach { (playerId, answer) ->
            val player = room.players.find { it.id == playerId }
            if (player != null) {
                val isCorrect = answer.equals(question.correctAnswer, ignoreCase = true)
                if (isCorrect) {
                    room.cursorPosition += if (room.players.indexOf(player) == 0) -0.1f else 0.1f
                }

                broadcastToRoom(
                    roomId,
                    json.encodeToString(
                        GameMessage.serializer(),
                        GameMessage.AnswerResult(
                            playerName = player.name,
                            answer = answer,
                            correct = isCorrect
                        )
                    )
                )
            }
        }

        // Oyun bitişini kontrol et
        if (room.cursorPosition <= 0f || room.cursorPosition >= 1f) {
            val winner = if (room.cursorPosition <= 0f) room.players[0].name else room.players[1].name
            broadcastToRoom(
                roomId,
                json.encodeToString(
                    GameMessage.serializer(),
                    GameMessage.GameOver(winner = winner)
                )
            )
            delay(GAME_OVER_DELAY_SECONDS * 1000)
            cleanupRoom(roomId)
        } else {
            delay(NEXT_QUESTION_DELAY_MS)
            nextQuestion(roomId)
        }
    }

    suspend fun handleDisconnect(playerId: String) {
        val roomId = playerToRoom[playerId] ?: return
        val room = rooms[roomId] ?: return
        val player = room.players.find { it.id == playerId } ?: return

        // Oyuncuyu bağlantısı kopmuş olarak işaretle
        disconnectedPlayers[playerId] = DisconnectedPlayer(
            playerId = playerId,
            playerName = player.name,
            roomId = roomId
        )

        // Diğer oyuncuya bildir
        val disconnectMessage = GameMessage.ConnectionState(
            type = ConnectionStateType.DISCONNECTED,
            playerId = playerId,
            playerName = player.name
        )
        room.players.filter { it.id != playerId }.forEach { otherPlayer ->
            playerSessions[otherPlayer.id]?.send(
                Frame.Text(json.encodeToString(GameMessage.serializer(), disconnectMessage))
            )
        }

        // Oyunu duraklat
        room.gameState = GameState.PAUSED
        roundTimers[roomId]?.cancel()

        // Yeniden bağlanma için süre başlat
        gameScope.launch {
            delay(RECONNECT_TIMEOUT_SECONDS * 1000)
            if (disconnectedPlayers.containsKey(playerId)) {
                cleanupRoom(roomId)
            }
        }
    }

    private suspend fun handleReconnect(playerId: String, session: DefaultWebSocketSession): Boolean {
        val disconnectedPlayer = disconnectedPlayers[playerId] ?: return false
        val room = rooms[disconnectedPlayer.roomId] ?: return false

        // Oyuncuyu yeniden bağla
        playerSessions[playerId] = session
        playerToRoom[playerId] = disconnectedPlayer.roomId
        disconnectedPlayers.remove(playerId)

        // Diğer oyuncuya bildir
        val reconnectMessage = GameMessage.ConnectionState(
            type = ConnectionStateType.RECONNECT_SUCCESS,
            playerId = playerId,
            playerName = disconnectedPlayer.playerName
        )
        room.players.filter { it.id != playerId }.forEach { otherPlayer ->
            playerSessions[otherPlayer.id]?.send(
                Frame.Text(json.encodeToString(GameMessage.serializer(), reconnectMessage))
            )
        }

        // Mevcut oyun durumunu gönder
        broadcastGameState(disconnectedPlayer.roomId)

        // Oyunu devam ettir
        if (room.gameState == GameState.PAUSED) {
            room.gameState = GameState.PLAYING
            nextQuestion(disconnectedPlayer.roomId)
        }

        return true
    }

    private suspend fun handlePlayerAnswer(playerId: String, message: GameMessage.PlayerAnswer) {
        val roomId = playerToRoom[playerId] ?: return
        val answers = roundAnswers.getOrPut(roomId) { mutableMapOf() }

        // Her oyuncu sadece bir kez cevap verebilir
        if (!answers.containsKey(playerId)) {
            answers[playerId] = message.answer
        }
    }

    private suspend fun cleanupRoom(roomId: String) {
        val room = rooms[roomId] ?: return

        // Odadaki oyunculara bildir
        room.players.forEach { player ->
            playerSessions[player.id]?.let { session ->
                try {
                    val message = GameMessage.GameOver(reason = "Oda kapatıldı")
                    session.send(Frame.Text(json.encodeToString(GameMessage.serializer(), message)))
                } catch (e: Exception) {
                    // İgnore send errors during cleanup
                }
            }
            playerToRoom.remove(player.id)
            playerSessions.remove(player.id)
            disconnectedPlayers.remove(player.id)
        }

        // Oda verilerini temizle
        rooms.remove(roomId)
        currentQuestions.remove(roomId)
        roundAnswers.remove(roomId)
        roundTimers[roomId]?.cancel()
        roundTimers.remove(roomId)
    }
}

@Serializable
data class ActiveRoom(
    val id: String,
    val playerCount: Int,
    val gameState: GameState,
    val players: List<String>
)

data class DisconnectedPlayer(
    val playerId: String,
    val playerName: String,
    val roomId: String,
    val disconnectTime: Long = System.currentTimeMillis()
)