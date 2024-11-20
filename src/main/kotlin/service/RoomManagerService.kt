package service

import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.*
import dto.GameRoomDTO
import response.DisconnectedPlayer
import response.ServerSocketMessage
import java.util.*

/**
 * @author guvencenanguvenal
 */
class RoomManagerService private constructor() {
    companion object {
        val INSTANCE: RoomManagerService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomManagerService() }
    }

    private val FLAG_CATEGORY = 1

    private val json = Json { ignoreUnknownKeys = true }

    private val gameScope = CoroutineScope(Dispatchers.Default + Job())

    private val rooms = Collections.synchronizedMap(mutableMapOf<String, GameRoom>())

    private val playerToRoom = Collections.synchronizedMap(mutableMapOf<String, String>())

    private val disconnectedPlayers = Collections.synchronizedMap(mutableMapOf<String, DisconnectedPlayer>())

    fun getRoomIdFromPlayerId(playerId: String): String {
        return playerToRoom[playerId]!!
    }

    fun createRoom(playerId: String): String {
        val roomId = UUID.randomUUID().toString()
        val room = GameRoom(roomId)
        val player = PlayerManagerService.INSTANCE.getPlayer(playerId) ?: return roomId //TODO: error message atilmali
        room.players.add(player)
        rooms[roomId] = room
        playerToRoom[playerId] = roomId
        println("Room $roomId created by player $playerId")
        return roomId
    }

    suspend fun joinRoom(playerId: String, roomId: String): Boolean {
        val player = PlayerManagerService.INSTANCE.getPlayer(playerId) ?: return false
        val room = rooms[roomId] ?: return false
        //TODO odaya istedigi kadar kisi katilabilecek
        if (room.players.size >= 2) return false

        room.players.add(player)
        playerToRoom[playerId] = roomId
        println("Player $playerId joined room $roomId")
        broadcastRoomState(roomId)
        return true
    }

    suspend fun rejoinRoom(playerId: String, roomId: String): Boolean {
        PlayerManagerService.INSTANCE.getPlayer(playerId) ?: return false
        val room = rooms[roomId] ?: return false

        disconnectedPlayers[playerId] ?: return false
        room.roomState = RoomState.PLAYING
        playerToRoom[playerId] = roomId
        println("Player $playerId joined room $roomId")
        broadcastRoomState(roomId)
        return true
    }

    private suspend fun cleanupRoom(room: GameRoom) {
        room.players.forEach { player -> SessionManagerService.INSTANCE.removePlayerSession(player.id) }
        // Oda verilerini temizle
        if (room.rounds.size > 0) {
            room.rounds.last().timer?.cancel()
        }
        rooms.remove(room.id)
    }

    suspend fun startGame(roomId: String) {
        val room = rooms[roomId] ?: return
        //default resistanceGame start
        val game = ResistanceGame(roomId, FLAG_CATEGORY)
        room.game = game

        println("Starting game for room $roomId with ${room.players.size} players")
        if (room.players.size != room.game!!.maxPlayerCount()) return

        gameScope.launch {
            println("Starting countdown for room $roomId")
            room.roomState = RoomState.COUNTDOWN
            broadcastRoomState(roomId)

            println("Waiting 3 seconds...")
            delay(3000)

            println("Starting actual game for room $roomId")
            room.roomState = RoomState.PLAYING
            nextQuestion(room)
        }
    }

    suspend fun continueGame(roomId: String) {
        val room = rooms[roomId] ?: return

        println("Starting game for room $roomId with ${room.players.size} players")
        if (room.players.size != room.game!!.maxPlayerCount()) return

        gameScope.launch {
            println("Starting countdown for room $roomId")
            room.roomState = RoomState.COUNTDOWN
            broadcastRoomState(roomId)

            println("Waiting 3 seconds...")
            delay(3000)

            println("Starting actual game for room $roomId")
            room.roomState = RoomState.PLAYING
            nextQuestion(room)
        }
    }

    private suspend fun broadcastRoomState(roomId: String) {
        println("Broadcasting game state for room $roomId")
        val room = rooms[roomId] ?: return
        val resistanceGame = room.game as ResistanceGame?

        val gameUpdate = ServerSocketMessage.RoomUpdate(
            players = room.players,
            state = room.roomState,
            cursorPosition = resistanceGame?.cursorPosition ?: 0.5f,
            currentQuestion = room.game?.currentQuestion?.toDTO()
        )
        broadcastToRoom(roomId, gameUpdate)
    }

    private suspend fun nextQuestion(room: GameRoom) {
        val question = room.game!!.nextQuestion()
        //TODO: gameleri yoneten bir yapi kurulmali
        val resistanceGame = room.game as ResistanceGame?

        val gameUpdate = ServerSocketMessage.RoomUpdate(
            players = room.players,
            state = RoomState.PLAYING,
            cursorPosition = resistanceGame?.cursorPosition ?: 0.5f,
            timeRemaining = room.game!!.getRoundTime(),
            currentQuestion = question.toDTO()
        )

        broadcastToRoom(room.id, gameUpdate)
        startRound(room.id)
    }

    private fun startRound(roomId: String) {
        val room = rooms[roomId]!!
        val roundNumber = room.rounds.size + 1
        room.rounds.add(Round(roundNumber))
        room.rounds.last().timer = CoroutineScope(Dispatchers.Default).launch {
            try {
                for (timeLeft in room.game!!.getRoundTime() - 1 downTo 1) {
                    delay(1000)
                    val timeUpdate = ServerSocketMessage.TimeUpdate(remaining = timeLeft)
                    broadcastToRoom(roomId, timeUpdate)
                }
                delay(1000)
                // Süre doldu
                room.rounds.last().answer = null

                // Süre doldu mesajı
                val timeUpMessage = ServerSocketMessage.TimeUp(correctAnswer = room.game?.currentQuestion?.answer!!)
                broadcastToRoom(roomId, timeUpMessage)

                endRound(roomId)
            } catch (e: CancellationException) {
                // Timer iptal edildi
            }
        }
    }

    private suspend fun endRound(roomId: String) {
        val room = rooms[roomId] ?: return
        val answer = room.rounds.last().answer
        val answeredPlayerId = room.rounds.last().answeredPlayer?.id
        //TODO: gameleri yoneten bir yapi kurulmali
        val resistanceGame = room.game!! as ResistanceGame

        room.rounds.last().timer?.cancel()

        val isCorrect = room.game!!.processAnswer(room.players, answeredPlayerId, answer)

        if (resistanceGame.cursorPosition <= 0f || resistanceGame.cursorPosition >= 1f) {
            room.roomState = RoomState.FINISHED
            broadcastRoomState(roomId)
            val gameOverMessage = ServerSocketMessage.GameOver(winnerPlayerId = room.rounds.last().answeredPlayer?.id!!)
            broadcastToRoom(roomId, gameOverMessage)

            delay(5000)
            cleanupRoom(room)
        } else {
            val roundResult = ServerSocketMessage.RoundResult(
                correctAnswer = resistanceGame.currentQuestion!!.answer,
                winnerPlayerId = if (isCorrect) room.rounds.last().answeredPlayer?.id!! else null
            )
            broadcastToRoom(roomId, roundResult)
            nextQuestion(room)
        }
    }

    private suspend fun broadcastToRoom(roomId: String, message: ServerSocketMessage) {
        println("Broadcasting message to room $roomId: $message")
        val room = rooms[roomId] ?: return
        val playerIds = room.players.map(Player::id).toMutableList()
        SessionManagerService.INSTANCE.broadcastToPlayers(playerIds, message)
    }

    fun getActiveRooms(): List<GameRoomDTO> {
        return rooms.map { (id, room) ->
            GameRoomDTO(
                id = id,
                playerCount = room.players.size,
                roomState = room.roomState,
                players = room.players.map { it.name }
            )
        }
    }

    suspend fun playerAnswered(roomId: String, playerId: String, answer: Int) {
        val room = rooms[roomId] ?: return
        val question = room.game!!.currentQuestion ?: return
        val player = room.players.find { it.id == playerId } ?: return

        //iki kullanici da bilemedi
        if ((room.rounds.last().answer != null) && (answer != question.answer)) {
            val answerResult = ServerSocketMessage.AnswerResult(
                playerId = player.id,
                answer = answer,
                correct = false
            )
            broadcastToRoom(roomId, answerResult)
            endRound(roomId)
            return
        }

        //hali hazirda dogru yanit varsa ikinci yaniti handle etme
        if (question.answer == room.rounds.last().answer) {
            return
        }

        room.rounds.last().answer = answer
        room.rounds.last().answeredPlayer = player

        val answerResult = ServerSocketMessage.AnswerResult(
            playerId = player.id,
            answer = answer,
            correct = answer == question.answer
        )
        broadcastToRoom(roomId, answerResult)

        if (answer == question.answer) {
            endRound(roomId)
        }
    }

    suspend fun playerDisconnected(playerId: String) {
        val roomId = playerToRoom[playerId]
        if (roomId != null) {
            val room = rooms[roomId]
            if (room != null) {
                val player = PlayerManagerService.INSTANCE.getPlayer(playerId)
                if (player != null) {
                    disconnectedPlayers[playerId] = DisconnectedPlayer(
                        playerId = playerId,
                        playerName = player.name,
                        roomId = roomId
                    )
                    room.players.remove(player)
                    playerToRoom.remove(playerId)

                    if(room.players.size == 0) {
                        cleanupRoom(room)
                        return
                    }

                    val disconnectMessage = ServerSocketMessage.PlayerDisconnected(playerId = player.id, playerName = player.name)
                    SessionManagerService.INSTANCE.broadcastToPlayers(room.players.filter { it.id != playerId }.map(Player::id).toMutableList(), disconnectMessage)

                    room.roomState = RoomState.PAUSED
                    room.rounds.last().timer?.cancel()
                    room.rounds.removeAt(room.rounds.size - 1)

                    // 30 saniye bekle ve oyuncu geri bağlanmazsa odayı temizle
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(30000)
                        if (room.roomState == RoomState.PAUSED) {
                            disconnectedPlayers.remove(playerId)
                            println("Player $playerId did not reconnect within 30 seconds, cleaning up room $roomId")
                            room.players.forEach { player ->
                                SessionManagerService.INSTANCE.getPlayerSession(player.id)?.let { session ->
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val message = ServerSocketMessage.RoomClosed(reason = "Player disconnected for too long")
                                        session.send(Frame.Text(json.encodeToString(message)))
                                    }
                                }
                                SessionManagerService.INSTANCE.removePlayerSession(player.id)
                            }
                            cleanupRoom(room)
                        }
                    }
                }
            }
        }
    }
}