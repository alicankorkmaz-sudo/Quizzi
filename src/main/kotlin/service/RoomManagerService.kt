package service

import dto.GameRoomDTO
import enums.RoomState
import kotlinx.coroutines.*
import model.GameRoom
import model.PlayerInRoom
import model.ResistanceGame
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
            processRound(room.id)
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

    private suspend fun processRound(roomId: String) {
        val room = roomService.getRoomById(roomId)
        val round = room.game.nextRound()
        val roundStarted = ServerSocketMessage.RoundStarted(
            roundNumber = round.number,
            timeRemaining = room.game.getRoundTime(),
            currentQuestion = room.game.getLastRound().question.toDTO()
        )
        broadcastToRoom(room, roundStarted)
        round.job = CoroutineScope(Dispatchers.Default).launch {
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
                val timeUpMessage = ServerSocketMessage.TimeUp(correctAnswer = room.game.getLastRound().question.answer)
                broadcastToRoom(room, timeUpMessage)

                //TODO: gameleri yoneten bir yapi kurulmali
                val resistanceGame = room.game as ResistanceGame

                val roundEnded = ServerSocketMessage.RoundEnded(
                    cursorPosition = resistanceGame.cursorPosition,
                    correctAnswer = resistanceGame.getLastRound().question.answer,
                    winnerPlayerId = null
                )
                broadcastToRoom(room, roundEnded)
                delay(500)
                processRound(roomId)
            } catch (e: CancellationException) {
                // Timer iptal edildi
            }
        }
    }

    private suspend fun interruptRound(roomId: String) {
        val room = roomService.getRoomById(roomId)
        val round = room.game.getLastRound()
        round.job?.cancel()

        //TODO: gameleri yoneten bir yapi kurulmali
        val resistanceGame = room.game as ResistanceGame

        val roundEnded = ServerSocketMessage.RoundEnded(
            cursorPosition = resistanceGame.cursorPosition,
            correctAnswer = resistanceGame.getLastRound().question.answer,
            winnerPlayerId = round.winnerPlayer?.id
        )
        broadcastToRoom(room, roundEnded)

        if (resistanceGame.isGameOver()) {
            room.roomState = RoomState.CLOSED
            broadcastRoomState(roomId)
            val gameOverMessage = ServerSocketMessage.GameOver(winnerPlayerId = round.winnerPlayer?.id!!)
            broadcastToRoom(room, gameOverMessage)
            roomService.cleanupRoom(room)
        } else {
            delay(500)
            processRound(roomId)
        }
    }

    suspend fun playerAnswered(roomId: String, playerId: String, answer: Int) {
        val room = roomService.getRoomById(roomId)
        val round = room.game.getLastRound()
        val question = room.game.getLastRound().question
        val answeredPlayer = room.players.find { it.id == playerId } ?: return

        //hali hazirda kazanan varsa ikinci yaniti handle etme
        if (round.winnerPlayer != null) {
            return
        }

        room.game.processAnswer(answeredPlayer, answer)

        val answerResult = ServerSocketMessage.AnswerResult(
            playerId = answeredPlayer.id,
            answer = answer,
            correct = answer == question.answer
        )
        broadcastToRoom(room, answerResult)

        if (round.winnerPlayer != null) {
            interruptRound(roomId)
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
            players = room.players.map(PlayerInRoom::toPlayerDTO).toMutableList(),
            state = room.roomState,
        )
        broadcastToRoom(room, gameUpdate)
    }

    private suspend fun broadcastToRoom(room: GameRoom, message: ServerSocketMessage) {
        println("Broadcasting message to room ${room.id}: $message")
        val playerIds = room.players.map(PlayerInRoom::id).toMutableList()
        SessionManagerService.INSTANCE.broadcastToPlayers(playerIds, message)
    }
}