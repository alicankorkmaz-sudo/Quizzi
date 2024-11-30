package service

import dto.GameRoomDTO
import dto.PlayerDTO
import enums.RoomState
import kotlinx.coroutines.*
import model.GameRoom
import model.ResistanceGame
import model.Round
import response.ServerSocketMessage
import service.internal.RoomService
import java.util.*

/**
 * @author guvencenanguvenal
 */
class RoomManagerService private constructor() {
    companion object {
        val INSTANCE: RoomManagerService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomManagerService() }
        const val COUNTDOWN_TIME = 3L
    }

    private val roomService: RoomService = RoomService()

    private val gameScope = CoroutineScope(Dispatchers.Default + Job())

    fun getRoomIdFromPlayerId(playerId: String): String {
        return roomService.getRoomIdFromPlayerId(playerId)
    }

    suspend fun createRoom(name: String, playerId: String, gameType: String): String {
        val gameId = UUID.randomUUID().toString()
        val game = GameFactory.INSTANCE.createGame(gameId, GameFactory.CategoryType.FLAGS, gameType)
        val creatorPlayer = PlayerManagerService.INSTANCE.getPlayer(playerId)
        val roomId = roomService.createRoom(name, creatorPlayer, game)
        broadcastRoomState(roomId)
        return roomId
    }

    suspend fun joinRoom(playerId: String, roomId: String): Boolean {
        val player = PlayerManagerService.INSTANCE.getPlayer(playerId)
        val isJoined = roomService.joinRoom(player, roomId)
        if (isJoined) {
            println("Player $playerId joined room $roomId")
            broadcastRoomState(roomId)
            return true
        }
        return false
    }

    fun rejoinRoom(playerId: String, roomId: String): Boolean {
        val player = PlayerManagerService.INSTANCE.getPlayer(playerId)
        val isRejoined = roomService.rejoinRoom(player, roomId)
        if (isRejoined) {
            println("Player $playerId joined room $roomId")
            return true
        }
        return false
    }

    fun playerReady(playerId: String): Boolean {
        roomService.playerReady(playerId)
        val roomId = roomService.getRoomIdFromPlayerId(playerId)
        return roomService.isAllPlayerReady(roomId)
    }

    fun startGame(roomId: String) {
        val room = roomService.getRoomById(roomId)
        println("Starting game for room $roomId with ${room.players.size} players")
        if (room.players.size != room.game.maxPlayerCount()) return

        gameScope.launch {
            countdownBeforeStart(room)
            println("Starting actual game for room ${room.id}")
            room.roomState = RoomState.PLAYING
            broadcastRoomState(room.id)
            startRound(room.id)
        }
    }

    private suspend fun countdownBeforeStart(room: GameRoom) {
        println("Starting countdown for room ${room.id}")
        room.roomState = RoomState.COUNTDOWN
        broadcastRoomState(room.id)

        for (timeLeft in COUNTDOWN_TIME downTo 1) {
            delay(1000)
            val countdownTimeUpdate = ServerSocketMessage.CountdownTimeUpdate(remaining = timeLeft)
            broadcastToRoom(room, countdownTimeUpdate)
        }
        delay(1000)
    }

    private suspend fun startRound(roomId: String) {
        val room = roomService.getRoomById(roomId)
        val round = room.game.nextRound()
        val roundEnded = ServerSocketMessage.RoundStarted(
            roundNumber = round.number,
            timeRemaining = room.game.getRoundTime(),
            currentQuestion = room.game.currentQuestion!!.toDTO()
        )
        broadcastToRoom(room, roundEnded)
        round.timer = CoroutineScope(Dispatchers.Default).launch {
            try {
                for (timeLeft in room.game.getRoundTime() - 1 downTo 1) {
                    delay(1000)
                    val timeUpdate = ServerSocketMessage.TimeUpdate(remaining = timeLeft)
                    broadcastToRoom(room, timeUpdate)
                }
                delay(1000)
                // Süre doldu
                round.answer = null
                // Süre doldu mesajı
                val timeUpMessage = ServerSocketMessage.TimeUp(correctAnswer = room.game.currentQuestion?.answer!!)
                broadcastToRoom(room, timeUpMessage)
                endRound(roomId)
            } catch (e: CancellationException) {
                // Timer iptal edildi
            }
        }
    }

    private suspend fun endRound(roomId: String) {
        val room = roomService.getRoomById(roomId)
        val round = room.game.getLastRound()
        val answer = round.answer
        val answeredPlayerId = round.answeredPlayer?.id
        //TODO: gameleri yoneten bir yapi kurulmali
        val resistanceGame = room.game as ResistanceGame

        round.timer?.cancel()

        val isCorrect = room.game.processAnswer(room.players, answeredPlayerId, answer)

        val roundEnded = ServerSocketMessage.RoundEnded(
            cursorPosition = resistanceGame.cursorPosition,
            correctAnswer = resistanceGame.currentQuestion!!.answer,
            winnerPlayerId = if (isCorrect) round.answeredPlayer?.id!! else null
        )
        broadcastToRoom(room, roundEnded)

        if (resistanceGame.cursorPosition <= 0f || resistanceGame.cursorPosition >= 1f) {
            room.roomState = RoomState.CLOSED
            broadcastRoomState(roomId)
            val gameOverMessage = ServerSocketMessage.GameOver(winnerPlayerId = round.answeredPlayer?.id!!)
            broadcastToRoom(room, gameOverMessage)
            roomService.cleanupRoom(room)
        } else {
            startRound(roomId)
        }
    }

    suspend fun playerAnswered(roomId: String, playerId: String, answer: Int) {
        val room = roomService.getRoomById(roomId)
        val question = room.game.currentQuestion ?: return
        val player = room.players.find { it.id == playerId } ?: return

        //iki kullanici da bilemedi
        if ((room.game.rounds.last().answer != null) && (answer != question.answer)) {
            val answerResult = ServerSocketMessage.AnswerResult(
                playerId = player.id,
                answer = answer,
                correct = false
            )
            broadcastToRoom(room, answerResult)
            endRound(roomId)
            return
        }

        //hali hazirda dogru yanit varsa ikinci yaniti handle etme
        if (question.answer == room.game.rounds.last().answer) {
            return
        }

        room.game.rounds.last().answer = answer
        room.game.rounds.last().answeredPlayer = player

        val answerResult = ServerSocketMessage.AnswerResult(
            playerId = player.id,
            answer = answer,
            correct = answer == question.answer
        )
        broadcastToRoom(room, answerResult)

        if (answer == question.answer) {
            endRound(roomId)
        }
    }

    suspend fun playerDisconnected(playerId: String) {
        roomService.playerDisconnected(playerId)
    }

    fun getActiveRooms(): List<GameRoomDTO> {
        return roomService.getAllRooms().map { (id, room) ->
            GameRoomDTO(
                id = id,
                name = room.name,
                playerCount = room.players.size,
                roomState = room.roomState,
                players = room.players.map { it.name }
            )
        }
    }

    private suspend fun broadcastRoomState(roomId: String) {
        println("Broadcasting game state for room $roomId")
        val room = roomService.getRoomById(roomId)

        val gameUpdate = ServerSocketMessage.RoomUpdate(
            players = room.players,
            state = room.roomState,
        )
        broadcastToRoom(room, gameUpdate)
    }

    private suspend fun broadcastToRoom(room: GameRoom, message: ServerSocketMessage) {
        println("Broadcasting message to room ${room.id}: $message")
        val playerIds = room.players.map(PlayerDTO::id).toMutableList()
        SessionManagerService.INSTANCE.broadcastToPlayers(playerIds, message)
    }
}