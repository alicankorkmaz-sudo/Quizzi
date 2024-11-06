package service

import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import models.*
import models.GameMessage.ConnectionStateType
import service.game.GameStateManager
import service.game.GameStateManager.Companion.NEXT_QUESTION_DELAY_MS
import service.player.PlayerManager
import service.room.RoomManager
import util.Logger

class GameService {
    private val gameScope = CoroutineScope(Dispatchers.Default + Job())
    private val json = Json { ignoreUnknownKeys = true }

    private val roomManager = RoomManager()
    private val playerManager = PlayerManager(gameScope, json)
    private val gameStateManager = GameStateManager(gameScope, json)

    companion object {
        const val RECONNECT_TIMEOUT_SECONDS = 30L
        const val GAME_OVER_DELAY_SECONDS = 5L
        const val COUNTDOWN_SECONDS = 3L
    }

    suspend fun registerPlayerSession(playerId: String, session: DefaultWebSocketSession) {
        playerManager.registerSession(playerId, session)
    }

    suspend fun handleMessage(playerId: String, message: String) {
        val gameMessage = try {
            json.decodeFromString<GameMessage>(message)
        } catch (e: Exception) {
            Logger.e("Mesaj decode edilemedi: $message", e)
            return
        }

        try {
            when (gameMessage) {
                is GameMessage.ConnectionState -> handleConnectionState(playerId, gameMessage)
                is GameMessage.CreateRoom -> handleCreateRoom(playerId, gameMessage)
                is GameMessage.JoinRoom -> handleJoinRoom(playerId, gameMessage)
                is GameMessage.PlayerAnswer -> handlePlayerAnswer(playerId, gameMessage)
                else -> {
                    Logger.w("Bilinmeyen mesaj tipi: ${gameMessage::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            Logger.e("Mesaj işlenirken hata oluştu: $gameMessage", e)
            handleError(playerId, e)
        }
    }

    private suspend fun handleCreateRoom(playerId: String, message: GameMessage.CreateRoom) {
        try {
            val roomId = roomManager.createRoom(playerId, message.playerName)
            Logger.d("Oda oluşturuldu: $roomId (Oyuncu: $playerId)")

            val response = GameMessage.RoomCreated(roomId = roomId)
            playerManager.sendToPlayer(playerId, json.encodeToString(GameMessage.serializer(), response))
        } catch (e: Exception) {
            Logger.e("Oda oluşturulurken hata: (Oyuncu: $playerId)", e)
            handleError(playerId, e)
        }
    }

    private suspend fun handleJoinRoom(playerId: String, message: GameMessage.JoinRoom) {
        try {
            val success = roomManager.joinRoom(playerId, message.roomId, message.playerName)
            Logger.d("Odaya katılma girişimi: ${message.roomId} - Başarılı: $success")

            val response = GameMessage.JoinRoomResponse(message.roomId, success = success)
            playerManager.sendToPlayer(playerId, json.encodeToString(GameMessage.serializer(), response))

            if (success) {
                startGame(message.roomId)
            }
        } catch (e: Exception) {
            Logger.e("Odaya katılırken hata: ${message.roomId} (Oyuncu: $playerId)", e)
            handleError(playerId, e)
        }
    }

    private suspend fun handleConnectionState(
        playerId: String,
        message: GameMessage.ConnectionState
    ) {
        when (message.connectionStateType) {
            ConnectionStateType.RECONNECT_REQUEST -> handleReconnectRequest(message)
            ConnectionStateType.DISCONNECTED -> handleDisconnect(playerId)
            else -> Unit
        }
    }

    private suspend fun handleReconnectRequest(message: GameMessage.ConnectionState) {
        val session = playerManager.getSession(message.playerId) ?: return
        val success = handleReconnect(message.playerId, session)

        val responseType = if (success) {
            ConnectionStateType.RECONNECT_SUCCESS
        } else {
            ConnectionStateType.RECONNECT_FAILED
        }

        playerManager.sendToPlayer(
            message.playerId,
            json.encodeToString(
                GameMessage.serializer(),
                GameMessage.ConnectionState(
                    connectionStateType = responseType,
                    playerId = message.playerId,
                    playerName = playerManager.getDisconnectedPlayer(message.playerId)?.playerName
                )
            )
        )
    }

    suspend fun startGame(roomId: String) {
        val room = roomManager.getRoom(roomId) ?: return
        if (room.players.size != 2) return

        gameScope.launch {
            roomManager.updateGameState(roomId, GameState.COUNTDOWN)
            broadcastGameState(roomId)
            delay(COUNTDOWN_SECONDS * 1000)

            roomManager.updateGameState(roomId, GameState.PLAYING)
            startNewRound(roomId)
        }
    }

    private suspend fun startNewRound(roomId: String) {
        val question = gameStateManager.startNewRound(roomId)
        val room = roomManager.getRoom(roomId) ?: return

        val gameUpdate = GameMessage.GameUpdate(
            gameState = GameState.PLAYING,
            cursorPosition = room.cursorPosition,
            timeRemaining = GameStateManager.ROUND_TIME_SECONDS,
            currentQuestion = question.toClientQuestion()
        )

        broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), gameUpdate))

        gameStateManager.startRoundTimer(
            roomId,
            onTick = { timeLeft ->
                broadcastToRoom(
                    roomId,
                    json.encodeToString(
                        GameMessage.serializer(),
                        GameMessage.TimeUpdate(timeRemaining = timeLeft)
                    )
                )
            },
            onComplete = {
                handleRoundEnd(roomId)
            }
        )
    }

    private suspend fun handleRoundEnd(roomId: String) {
        val room = roomManager.getRoom(roomId) ?: return
        val question = gameStateManager.getCurrentQuestion(roomId) ?: return

        try {
            // Cevap vermeyen oyuncuları işle
            val answers = gameStateManager.getAnswers(roomId) ?: emptyMap()
            processUnansweredPlayers(room, answers)

            // Doğru cevabı yayınla
            broadcastCorrectAnswer(roomId, question)

            // Oyun bitip bitmediğini kontrol et
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
                // Kısa bir bekleme süresi
                delay(NEXT_QUESTION_DELAY_MS)
                // Yeni tura geç
                startNewRound(roomId)
            }
        } catch (e: Exception) {
            Logger.e("Tur sonu işlenirken hata oluştu: $roomId", e)
            handleRoomError(roomId, e)
        }
    }

    suspend fun handleDisconnect(playerId: String) {
        val roomId = roomManager.getPlayerRoom(playerId) ?: return
        val room = roomManager.getRoom(roomId) ?: return
        val player = room.players.find { it.id == playerId } ?: return

        playerManager.markDisconnected(playerId, player.name, roomId)

        // Diğer oyunculara bildir
        val otherPlayers = room.players.filter { it.id != playerId }.map { it.id }
        playerManager.notifyDisconnection(playerId, player.name, otherPlayers)

        // Oyunu duraklat
        roomManager.updateGameState(roomId, GameState.PAUSED)
        gameStateManager.cancelTimer(roomId)

        // Yeniden bağlanma için süre başlat
        gameScope.launch {
            delay(RECONNECT_TIMEOUT_SECONDS * 1000)
            if (playerManager.isDisconnected(playerId)) {
                cleanupRoom(roomId)
            }
        }
    }

    private suspend fun handleReconnect(playerId: String, session: DefaultWebSocketSession): Boolean {
        val disconnectedPlayer = playerManager.reconnectPlayer(playerId, session) ?: return false
        val room = roomManager.getRoom(disconnectedPlayer.roomId) ?: return false

        // Diğer oyunculara bildir
        val otherPlayers = room.players.filter { it.id != playerId }.map { it.id }
        playerManager.notifyReconnection(playerId, disconnectedPlayer.playerName, otherPlayers)

        // Mevcut oyun durumunu gönder
        broadcastGameState(disconnectedPlayer.roomId)

        // Oyunu devam ettir
        if (room.gameState == GameState.PAUSED) {
            roomManager.updateGameState(disconnectedPlayer.roomId, GameState.PLAYING)
            startNewRound(disconnectedPlayer.roomId)
        }

        return true
    }

    private suspend fun handlePlayerAnswer(playerId: String, message: GameMessage.PlayerAnswer) {
        val roomId = roomManager.getPlayerRoom(playerId) ?: return
        val room = roomManager.getRoom(roomId) ?: return
        val player = room.players.find { it.id == playerId } ?: return
        val question = gameStateManager.getCurrentQuestion(roomId) ?: return

        processPlayerAnswer(roomId, room, player, message.answer, question)
    }

    private suspend fun cleanupRoom(roomId: String) {
        val room = roomManager.getRoom(roomId) ?: return

        // Odadaki oyunculara bildir
        room.players.forEach { player ->
            try {
                playerManager.sendToPlayer(
                    player.id,
                    json.encodeToString(
                        GameMessage.serializer(),
                        GameMessage.GameOver(reason = "Oda kapatıldı")
                    )
                )
            } catch (e: Exception) {
                // Cleanup sırasındaki hataları görmezden gel
            }

            playerManager.removePlayer(player.id)
        }

        // Oda verilerini temizle
        roomManager.removeRoom(roomId)
        gameStateManager.cleanup(roomId)
    }

    private suspend fun broadcastToRoom(roomId: String, message: String) {
        val room = roomManager.getRoom(roomId) ?: run {
            Logger.w("Broadcast yapılamadı - Oda bulunamadı: $roomId")
            return
        }

        room.players.forEach { player ->
            try {
                playerManager.sendToPlayer(player.id, message)
            } catch (e: Exception) {
                Logger.e("Broadcast sırasında hata: ${player.id}", e)
                handleDisconnect(player.id)
            }
        }
    }

    private suspend fun broadcastGameState(roomId: String) {
        val room = roomManager.getRoom(roomId) ?: return
        val gameUpdate = GameMessage.GameUpdate(
            gameState = room.gameState,
            cursorPosition = room.cursorPosition,
            currentQuestion = gameStateManager.getCurrentQuestion(roomId)?.toClientQuestion()
        )
        broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), gameUpdate))
    }

    fun getActiveRooms() = roomManager.getActiveRooms()

    private suspend fun handleError(playerId: String, error: Exception) {
        try {
            val errorMessage = GameMessage.Error(
                message = "Bir hata oluştu: ${error.message ?: "Bilinmeyen hata"}"
            )
            playerManager.sendToPlayer(
                playerId,
                json.encodeToString(GameMessage.serializer(), errorMessage)
            )
        } catch (e: Exception) {
            Logger.e("Hata mesajı gönderilirken ikincil hata oluştu: $playerId", e)
        }
    }

    private suspend fun handleRoomError(roomId: String, error: Exception) {
        try {
            val errorMessage = GameMessage.Error(
                message = error.message ?: "Odada bir hata oluştu. Oyun yeniden başlatılıyor."
            )
            broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), errorMessage))
            cleanupRoom(roomId)
        } catch (e: Exception) {
            Logger.e("Oda hatası işlenirken ikincil hata oluştu: $roomId", e)
        }
    }

    // handleRoundEnd fonksiyonunu böldük
    private suspend fun processUnansweredPlayers(room: GameRoom, answers: Map<String, String>) {
        room.players
            .filter { player -> !answers.containsKey(player.id) }
            .forEach { player ->
                Logger.d("Cevapsız oyuncu işlendi: ${player.id}")
                gameStateManager.recordAnswer(room.id, player.id, "")
            }
    }

    private suspend fun broadcastCorrectAnswer(roomId: String, question: Question) {
        try {
            broadcastToRoom(
                roomId,
                json.encodeToString(
                    GameMessage.serializer(),
                    GameMessage.TimeUp(correctAnswer = question.correctAnswer)
                )
            )
        } catch (e: Exception) {
            Logger.e("Doğru cevap broadcast edilirken hata: $roomId", e)
            throw e
        }
    }

    private suspend fun processPlayerAnswer(
        roomId: String,
        room: GameRoom,
        player: Player,
        answer: String,
        question: Question
    ) {
        val isCorrect = answer.equals(question.correctAnswer, ignoreCase = true)

        // Cevabı kaydet
        if (!gameStateManager.recordAnswer(roomId, player.id, answer)) {
            return // Oyuncu zaten cevap vermiş
        }

        // Sonucu yayınla
        val result = GameMessage.AnswerResult(
            playerName = player.name,
            answer = answer,
            correct = isCorrect
        )
        broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), result))

        if (isCorrect) {
            // Doğru cevap verildiğinde imleci güncelle ve roundu sonlandır
            val cursorDelta = if (player == room.players[0]) -0.1f else 0.1f
            roomManager.updateCursorPosition(roomId, cursorDelta)

            // Round'u sonlandır
            gameStateManager.cancelTimer(roomId)
            handleRoundEnd(roomId)
        } else {
            // Yanlış cevap kaydı
            gameStateManager.recordWrongAnswer(roomId, player.id)

            // İki oyuncu da yanlış cevap verdiyse
            if (gameStateManager.bothPlayersAnsweredWrong(roomId, room.players.size)) {
                gameStateManager.cancelTimer(roomId)
                handleRoundEnd(roomId)
            }
        }
    }
}