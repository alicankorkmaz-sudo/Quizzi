package service

import dto.GameRoomDTO
import enums.RoomEnumState
import exception.WrongCommandWrongTime
import kotlinx.coroutines.*
import model.GameRoom
import model.Player
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
        if (!room.isAllPlayerReady()) return
        room.startGame()
    }

    private suspend fun processRound(room: GameRoom) {
        //gameOverChecking
        if (room.game.gameOver()) {
            room.roomEnumState = RoomEnumState.CLOSED
            broadcastRoomState(room.id)
            val gameOverMessage =
                ServerSocketMessage.GameOver(winnerPlayerId = room.game.getLastRound().roundWinnerPlayer()?.id!!)
            room.broadcast(gameOverMessage)
            roomService.cleanupRoom(room)
            return
        }

        delay(2000)
        val round = room.game.nextRound()
        val roundStarted = ServerSocketMessage.RoundStarted(
            roundNumber = round.number,
            timeRemaining = room.game.getRoundTime(),
            currentQuestion = room.game.getLastRound().question.toDTO()
        )
        room.broadcast(roundStarted)
        round.job = CoroutineScope(Dispatchers.Default).launch {
            try {
                for (timeLeft in room.game.getRoundTime() - 1 downTo 1) {
                    delay(1000)
                    val timeUpdate = ServerSocketMessage.TimeUpdate(remaining = timeLeft)
                    room.broadcast(timeUpdate)
                }
                delay(1000)
                // Süre doldu mesajı
                val timeUpMessage = ServerSocketMessage.TimeUp(correctAnswer = room.game.getLastRound().question.answer)
                room.broadcast(timeUpMessage)

                val resistanceGame = room.game as ResistanceGame

                val roundEnded = ServerSocketMessage.RoundEnded(
                    cursorPosition = resistanceGame.cursorPosition,
                    correctAnswer = resistanceGame.getLastRound().question.answer,
                    winnerPlayerId = null
                )
                room.broadcast(roundEnded)

                if (room.roomEnumState == RoomEnumState.PLAYING) {
                    processRound(room)
                }
            } catch (e: CancellationException) {
                // Timer iptal edildi
            }
        }
    }

    private suspend fun interruptRound(room: GameRoom) {
        val round = room.game.getLastRound()
        //TODO: gameleri yoneten bir yapi kurulmali
        val resistanceGame = room.game as ResistanceGame

        round.job?.cancel()
        room.game.calculateResult(room.players)

        val roundEnded = ServerSocketMessage.RoundEnded(
            cursorPosition = resistanceGame.cursorPosition,
            correctAnswer = resistanceGame.getLastRound().question.answer,
            winnerPlayerId = round.roundWinnerPlayer()?.id
        )
        room.broadcast(roundEnded)

        if (room.roomEnumState == RoomEnumState.PLAYING && room.game.rounds.none { r -> r.job?.isActive == true }) {
            processRound(room)
        }
    }

    suspend fun playerAnswered(roomId: String, playerId: String, answer: Int) {
        val room = roomService.getRoomById(roomId)
        if (room.roomEnumState != RoomEnumState.PLAYING) throw WrongCommandWrongTime()
        val lastRound = room.game.getLastRound()
        val player = room.players.find { it.id == playerId } ?: return

        lastRound.playerAnswered(Player(player), answer)
        val roundOver = lastRound.isRoundOver(room.game.maxPlayerCount())

        val answerResult = ServerSocketMessage.AnswerResult(
            playerId = player.id,
            answer = answer,
            correct = lastRound.question.answer == answer
        )
        room.broadcast(answerResult)

        if (roundOver) {
            interruptRound(room)
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
                roomEnumState = room.roomEnumState,
                players = room.players.map { it.name }
            )
        }
    }

    private suspend fun broadcastRoomState(roomId: String) {
        println("Broadcasting game state for room $roomId")
        val room = roomService.getRoomById(roomId)

        val gameUpdate = ServerSocketMessage.RoomUpdate(
            players = room.players,
            state = room.roomEnumState,
        )
        room.broadcast(gameUpdate)
    }
}