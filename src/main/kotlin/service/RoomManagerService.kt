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
        val COUNTDOWN_TIME = 3L
    }

    private val roomService: RoomService = RoomService()

    private val gameScope = CoroutineScope(Dispatchers.Default + Job())

    fun getRoomIdFromPlayerId(playerId: String): String {
        return roomService.getRoomIdFromPlayerId(playerId)!!
    }

    fun createRoom(playerId: String, gameType: String): String? {
        val gameId = UUID.randomUUID().toString()
        val game = GameFactory.INSTANCE.createGame(gameId, GameFactory.CategoryType.FLAGS, gameType)
        val creatorPlayer = PlayerManagerService.INSTANCE.getPlayer(playerId) ?: return null //TODO error would throw
        return roomService.createRoom(creatorPlayer, game)
    }

    suspend fun joinRoom(playerId: String, roomId: String): Boolean {
        val player = PlayerManagerService.INSTANCE.getPlayer(playerId) ?: return false
        val isJoined = roomService.joinRoom(player, roomId)
        if (isJoined) {
            println("Player $playerId joined room $roomId")
            broadcastRoomState(roomId)
            return true
        }
        return false
    }

    suspend fun rejoinRoom(playerId: String, roomId: String): Boolean {
        val player = PlayerManagerService.INSTANCE.getPlayer(playerId) ?: return false
        val isRejoined = roomService.rejoinRoom(player, roomId)
        if (isRejoined) {
            println("Player $playerId joined room $roomId")
            broadcastRoomState(roomId)
            return true
        }
        return false
    }

    private suspend fun cleanupRoom(room: GameRoom) {
        roomService.cleanupRoom(room)
    }

    suspend fun startGame(roomId: String) {
        val room = roomService.getRoomById(roomId) ?: return
        //default resistanceGame start
        println("Starting game for room $roomId with ${room.players.size} players")
        if (room.players.size != room.game.maxPlayerCount()) return

        gameScope.launch {
            println("Starting countdown for room $roomId")
            room.roomState = RoomState.COUNTDOWN
            broadcastRoomState(roomId)

            for (timeLeft in COUNTDOWN_TIME downTo 1) {
                delay(1000)
                val countdownTimeUpdate = ServerSocketMessage.CountdownTimeUpdate(remaining = timeLeft)
                broadcastToRoom(roomId, countdownTimeUpdate)
            }
            delay(1000)

            println("Starting actual game for room $roomId")
            room.roomState = RoomState.PLAYING
            nextQuestion(room)
        }
    }

    suspend fun continueGame(roomId: String) {
        val room = roomService.getRoomById(roomId) ?: return

        println("Starting game for room $roomId with ${room.players.size} players")
        if (room.players.size != room.game.maxPlayerCount()) return

        gameScope.launch {
            println("Starting countdown for room $roomId")
            room.roomState = RoomState.COUNTDOWN
            broadcastRoomState(roomId)

            for (timeLeft in COUNTDOWN_TIME downTo 1) {
                delay(1000)
                val countdownTimeUpdate = ServerSocketMessage.CountdownTimeUpdate(remaining = timeLeft)
                broadcastToRoom(roomId, countdownTimeUpdate)
            }
            delay(1000)

            println("Starting actual game for room $roomId")
            room.roomState = RoomState.PLAYING
            nextQuestion(room)
        }
    }

    private suspend fun broadcastRoomState(roomId: String) {
        println("Broadcasting game state for room $roomId")
        val room = roomService.getRoomById(roomId) ?: return
        val resistanceGame = room.game as ResistanceGame?

        val gameUpdate = ServerSocketMessage.RoomUpdate(
            players = room.players,
            state = room.roomState,
            cursorPosition = resistanceGame?.cursorPosition ?: 0.5f,
            currentQuestion = room.game.currentQuestion?.toDTO()
        )
        broadcastToRoom(roomId, gameUpdate)
    }

    private suspend fun nextQuestion(room: GameRoom) {
        val question = room.game.nextQuestion()
        //TODO: gameleri yoneten bir yapi kurulmali
        val resistanceGame = room.game as ResistanceGame?

        val gameUpdate = ServerSocketMessage.RoomUpdate(
            players = room.players,
            state = RoomState.PLAYING,
            cursorPosition = resistanceGame?.cursorPosition ?: 0.5f,
            timeRemaining = room.game.getRoundTime(),
            currentQuestion = question.toDTO()
        )

        broadcastToRoom(room.id, gameUpdate)
        startRound(room.id)
    }

    private suspend fun startRound(roomId: String) {
        val room = roomService.getRoomById(roomId) ?: return
        val roundNumber = room.rounds.size + 1
        room.rounds.add(Round(roundNumber))
        val roundEnded = ServerSocketMessage.RoundStarted(
            roundNumber = roundNumber,
            currentQuestion = room.game.currentQuestion?.toDTO()
        )
        broadcastToRoom(roomId, roundEnded)
        room.rounds.last().timer = CoroutineScope(Dispatchers.Default).launch {
            try {
                for (timeLeft in room.game.getRoundTime() - 1 downTo 1) {
                    delay(1000)
                    val timeUpdate = ServerSocketMessage.TimeUpdate(remaining = timeLeft)
                    broadcastToRoom(roomId, timeUpdate)
                }
                delay(1000)
                // Süre doldu
                room.rounds.last().answer = null

                // Süre doldu mesajı
                val timeUpMessage = ServerSocketMessage.TimeUp(correctAnswer = room.game.currentQuestion?.answer!!)
                broadcastToRoom(roomId, timeUpMessage)

                endRound(roomId)
            } catch (e: CancellationException) {
                // Timer iptal edildi
            }
        }
    }

    private suspend fun endRound(roomId: String) {
        val room = roomService.getRoomById(roomId) ?: return
        val answer = room.rounds.last().answer
        val answeredPlayerId = room.rounds.last().answeredPlayer?.id
        //TODO: gameleri yoneten bir yapi kurulmali
        val resistanceGame = room.game as ResistanceGame

        room.rounds.last().timer?.cancel()

        val isCorrect = room.game.processAnswer(room.players, answeredPlayerId, answer)

        if (resistanceGame.cursorPosition <= 0f || resistanceGame.cursorPosition >= 1f) {
            room.roomState = RoomState.FINISHED
            broadcastRoomState(roomId)
            val gameOverMessage = ServerSocketMessage.GameOver(winnerPlayerId = room.rounds.last().answeredPlayer?.id!!)
            broadcastToRoom(roomId, gameOverMessage)

            delay(5000)
            cleanupRoom(room)
        } else {
            val roundEnded = ServerSocketMessage.RoundEnded(
                cursorPosition = resistanceGame.cursorPosition,
                correctAnswer = resistanceGame.currentQuestion!!.answer,
                winnerPlayerId = if (isCorrect) room.rounds.last().answeredPlayer?.id!! else null
            )
            broadcastToRoom(roomId, roundEnded)
            nextQuestion(room)
        }
    }

    private suspend fun broadcastToRoom(roomId: String, message: ServerSocketMessage) {
        println("Broadcasting message to room $roomId: $message")
        val room = roomService.getRoomById(roomId) ?: return
        val playerIds = room.players.map(Player::id).toMutableList()
        SessionManagerService.INSTANCE.broadcastToPlayers(playerIds, message)
    }

    fun getActiveRooms(): List<GameRoomDTO> {
        return roomService.getAllRooms().map { (id, room) ->
            GameRoomDTO(
                id = id,
                playerCount = room.players.size,
                roomState = room.roomState,
                players = room.players.map { it.name }
            )
        }
    }

    suspend fun playerAnswered(roomId: String, playerId: String, answer: Int) {
        val room = roomService.getRoomById(roomId) ?: return
        val question = room.game.currentQuestion ?: return
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
        roomService.playerDisconnected(playerId)
    }
}