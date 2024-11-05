package service

import data.FlagDatabase
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import models.*
import java.util.*

class GameService {
    private val gameStates = mutableMapOf<String, GameState>()
    private val gameScope = CoroutineScope(Dispatchers.Default + Job())
    private val currentQuestions = mutableMapOf<String, Question>()
    private val roundAnswers = mutableMapOf<String, MutableMap<String, String>>()
    private val roundTimers = mutableMapOf<String, Job>()  // Her odanın zamanlayıcısı
    private val json = Json { ignoreUnknownKeys = true }
    private val rooms = mutableMapOf<String, GameRoom>()
    private val playerSessions = mutableMapOf<String, DefaultWebSocketSession>()
    private val playerToRoom = mutableMapOf<String, String>()
    private val disconnectedPlayers = mutableMapOf<String, DisconnectedPlayer>()

    private val ROUND_TIME_SECONDS = 10L  // Her el için süre limiti

    fun createRoom(playerId: String, playerName: String): String {
        val roomId = UUID.randomUUID().toString()
        val player = Player(playerId, playerName)
        val room = GameRoom(roomId)
        room.players.add(player)
        rooms[roomId] = room
        playerToRoom[playerId] = roomId
        println("Room $roomId created by player $playerId")
        return roomId
    }

    fun joinRoom(playerId: String, roomId: String, playerName: String): Boolean {
        val room = rooms[roomId] ?: return false
        if (room.players.size >= 2) return false

        val player = Player(playerId, playerName)
        room.players.add(player)
        playerToRoom[playerId] = roomId
        println("Player $playerId joined room $roomId")
        return true
    }

    suspend fun registerPlayerSession(playerId: String, session: DefaultWebSocketSession) {
        println("Registering session for player $playerId")
        playerSessions[playerId] = session
        println("Current active sessions: ${playerSessions.keys}")
    }

    suspend fun startGame(roomId: String) {
        val room = rooms[roomId] ?: return
        println("Starting game for room $roomId with ${room.players.size} players")
        if (room.players.size != 2) return

        gameScope.launch {
            println("Starting countdown for room $roomId")
            // Geri sayım başlat
            room.gameState = GameState.COUNTDOWN
            broadcastGameState(roomId)

            println("Waiting 3 seconds...")
            // 3 saniye bekle
            delay(3000)

            println("Starting actual game for room $roomId")
            // Oyunu başlat
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
        // Önceki timer'ı iptal et
        roundTimers[roomId]?.cancel()

        // Yeni timer başlat
        roundTimers[roomId] = CoroutineScope(Dispatchers.Default).launch {
            try {
                // Kalan süreyi göster
                for (timeLeft in ROUND_TIME_SECONDS - 1 downTo 1) {
                    delay(1000)
                    val timeUpdate = GameMessage.TimeUpdate(timeRemaining = timeLeft)
                    broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), timeUpdate))
                }

                delay(1000)
                // Süre doldu
                handleRoundEnd(roomId)
            } catch (e: CancellationException) {
                // Timer iptal edildi
            }
        }
    }

    private suspend fun handleRoundEnd(roomId: String) {
        val room = rooms[roomId] ?: return
        val question = currentQuestions[roomId] ?: return
        val answers = roundAnswers[roomId] ?: mutableMapOf()

        // Süre doldu mesajı
        val timeUpMessage = GameMessage.TimeUp(correctAnswer = question.correctAnswer)
        broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), timeUpMessage))

        // Doğru cevap veren oyuncuyu bul
        val correctPlayer = room.players.find { p ->
            answers[p.id] == question.correctAnswer
        }

        if (correctPlayer != null) {
            // İmleç pozisyonunu güncelle
            val currentPosition = room.cursorPosition
            val movement = if (room.players.indexOf(correctPlayer) == 0) -0.1f else 0.1f
            val newPosition = currentPosition + movement
            room.cursorPosition = when {
                newPosition <= 0.1f -> 0f  // Sol limit
                newPosition >= 0.9f -> 1f  // Sağ limit
                else -> newPosition
            }

            // Son cursor pozisyonunu gönder
            val cursorUpdate = GameMessage.GameUpdate(
                gameState = room.gameState,
                cursorPosition = room.cursorPosition,
                currentQuestion = currentQuestions[roomId]?.toClientQuestion()
            )
            broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), cursorUpdate))

            if (room.cursorPosition <= 0f || room.cursorPosition >= 1f) {
                // Oyun bitti
                room.gameState = GameState.FINISHED

                // Son durum güncellemesi
                val finalUpdate = GameMessage.GameUpdate(
                    gameState = GameState.FINISHED,
                    cursorPosition = room.cursorPosition,
                    currentQuestion = null
                )
                broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), finalUpdate))

                // Kazanan bilgisi
                val gameOverMessage = GameMessage.GameOver(winner = correctPlayer.name)
                broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), gameOverMessage))

                // Odayı temizlemeden önce biraz bekle
                delay(5000)
                cleanupRoom(roomId)
            } else {
                // Yeni soruya geç
                delay(1500)
                nextQuestion(roomId)
            }
        } else {
            // Kimse doğru cevap veremediyse yeni soru
            delay(1500)
            nextQuestion(roomId)
        }
    }

    private fun cleanupRoom(roomId: String) {
        val room = rooms[roomId] ?: return

        // Odadaki oyunculara bildir
        room.players.forEach { player ->
            playerSessions[player.id]?.let { session ->
                CoroutineScope(Dispatchers.IO).launch {
                    val message = GameMessage.RoomClosed(reason = "Player disconnected for too long")
                    session.send(Frame.Text(json.encodeToString(GameMessage.serializer(), message)))
                }
            }

            // Oyuncu verilerini temizle
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

    suspend fun handlePlayerAnswer(roomId: String, playerId: String, answer: String) {
        val room = rooms[roomId] ?: return
        val question = currentQuestions[roomId] ?: return
        val player = room.players.find { it.id == playerId } ?: return

        // Cevabı kaydet
        roundAnswers.getOrPut(roomId) { mutableMapOf() }[playerId] = answer

        // Cevap sonucunu bildir
        val answerResult = GameMessage.AnswerResult(
            playerName = player.name,
            answer = answer,
            correct = answer == question.correctAnswer
        )

        broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), answerResult))

        // Doğru cevap verildiyse eli hemen sonlandır
        if (answer == question.correctAnswer) {
            roundTimers[roomId]?.cancel()  // Timer'ı iptal et
            handleRoundEnd(roomId)
        }
    }

    private suspend fun broadcastGameState(roomId: String) {
        println("Broadcasting game state for room $roomId")
        val room = rooms[roomId] ?: return

        val gameUpdate = GameMessage.GameUpdate(
            gameState = room.gameState,
            cursorPosition = room.cursorPosition,
            currentQuestion = currentQuestions[roomId]?.toClientQuestion()
        )

        println("Broadcasting game update: ${json.encodeToString(GameMessage.serializer(), gameUpdate)}")
        broadcastToRoom(roomId, json.encodeToString(GameMessage.serializer(), gameUpdate))
    }

    private suspend fun broadcastToRoom(roomId: String, message: String) {
        println("Broadcasting message to room $roomId: $message")
        val room = rooms[roomId] ?: return

        room.players.forEach { player ->
            val session = playerSessions[player.id]
            if (session != null) {
                try {
                    session.send(Frame.Text(message))
                    println("Message sent to player ${player.id}")
                } catch (e: Exception) {
                    println("Error sending message to player ${player.id}: ${e.message}")
                }
            } else {
                println("No session found for player ${player.id}")
            }
        }
    }

    suspend fun handleMessage(playerId: String, message: String) {
        try {
            val gameMessage = json.decodeFromString<GameMessage>(message)
            when (gameMessage) {
                is GameMessage.CreateRoom -> {
                    val roomId = createRoom(playerId, gameMessage.playerName)
                    val response = GameMessage.RoomCreated(
                        roomId = roomId
                    )
                    playerSessions[playerId]?.send(Frame.Text(json.encodeToString(GameMessage.serializer(), response)))
                }

                is GameMessage.JoinRoom -> {
                    val success = joinRoom(playerId, gameMessage.roomId, gameMessage.playerName)
                    val response = GameMessage.JoinRoomResponse(
                        gameMessage.roomId,
                        success = success
                    )
                    playerSessions[playerId]?.send(Frame.Text(json.encodeToString(GameMessage.serializer(), response)))
                    if (success) {
                        startGame(gameMessage.roomId)
                    }
                }

                is GameMessage.PlayerAnswer -> {
                    val roomId = playerToRoom[playerId]
                    if (roomId != null) {
                        handlePlayerAnswer(roomId, playerId, gameMessage.answer)
                    }
                }

                else -> {
                    println("Unexpected message type received: ${gameMessage::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            println("Error processing message: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun handleDisconnect(playerId: String) {
        val roomId = rooms.entries.find { entry ->
            entry.value.players.any { it.id == playerId }
        }?.key

        if (roomId != null) {
            val room = rooms[roomId]
            if (room != null) {
                val player = room.players.find { it.id == playerId }
                if (player != null) {
                    // Disconnected players listesine ekle
                    disconnectedPlayers[playerId] = DisconnectedPlayer(
                        playerId = playerId,
                        playerName = player.name,
                        roomId = roomId
                    )

                    // Diğer oyuncuya bildir
                    val disconnectMessage = GameMessage.PlayerDisconnected(playerName = player.name)
                    room.players.filter { it.id != playerId }.forEach { otherPlayer ->
                        playerSessions[otherPlayer.id]?.send(
                            Frame.Text(
                                json.encodeToString(
                                    GameMessage.serializer(),
                                    disconnectMessage
                                )
                            )
                        )
                    }

                    // Oyunu duraklatmak için GameState'i gncelle
                    room.gameState = GameState.PAUSED
                    roundTimers[roomId]?.cancel() // Timer'ı durdur

                    // 30 saniye bekle ve oyuncu geri bağlanmazsa odayı temizle
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(30000)
                        if (disconnectedPlayers.containsKey(playerId)) {
                            println("Player $playerId did not reconnect within 30 seconds, cleaning up room $roomId")
                            cleanupRoom(roomId)
                        }
                    }
                }
            }
        }

        playerSessions.remove(playerId)
    }


    suspend fun handleReconnect(playerId: String, session: DefaultWebSocketSession): Boolean {
        val disconnectedPlayer = disconnectedPlayers[playerId]
        if (disconnectedPlayer != null) {
            val room = rooms[disconnectedPlayer.roomId]
            if (room != null) {
                // Oyuncuyu yeniden bağla
                playerSessions[playerId] = session
                playerToRoom[playerId] = disconnectedPlayer.roomId
                disconnectedPlayers.remove(playerId)

                // Diğer oyuncuya bildir
                val reconnectMessage = GameMessage.PlayerReconnected(playerName = disconnectedPlayer.playerName)
                room.players.filter { it.id != playerId }.forEach { otherPlayer ->
                    playerSessions[otherPlayer.id]?.send(
                        Frame.Text(
                            json.encodeToString(
                                GameMessage.serializer(),
                                reconnectMessage
                            )
                        )
                    )
                }

                // Oyunu devam ettir
                if (room.gameState == GameState.PAUSED) {
                    room.gameState = GameState.PLAYING
                    nextQuestion(disconnectedPlayer.roomId)
                }

                return true
            }
        }
        return false
    }

    fun getActiveRooms(): List<ActiveRoom> {
        return rooms.map { (id, room) ->
            ActiveRoom(
                id = id,
                playerCount = room.players.size,
                gameState = room.gameState,
                players = room.players.map { it.name }
            )
        }
    }
}

@Serializable
data class ActiveRoom(
    val id: String,
    val playerCount: Int,
    val gameState: GameState,
    val players: List<String> // oyuncu isimleri
)

@Serializable
data class ActiveRoomsResponse(
    val rooms: List<ActiveRoom>
)

data class DisconnectedPlayer(
    val playerId: String,
    val playerName: String,
    val roomId: String,
    val disconnectTime: Long = System.currentTimeMillis()
)