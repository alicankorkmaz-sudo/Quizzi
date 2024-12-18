package model

import dto.PlayerDTO
import enums.*
import exception.TooMuchPlayersInRoom
import exception.WrongCommandWrongTime
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import response.ServerSocketMessage
import service.RoomBroadcastService
import service.RoomManagerService.Companion.COUNTDOWN_TIME
import java.util.*

@Serializable
data class GameRoom(
    val id: String,
    val name: String,
    val game: Game,
    val players: MutableList<PlayerDTO> = Collections.synchronizedList(mutableListOf()),
    var roomEnumState: RoomEnumState = RoomEnumState.WAITING,
) {
    private val gameScope = CoroutineScope(Dispatchers.Default + Job())

    private var state: RoomState = RoomState.Waiting

    fun getState(): RoomState = state

    suspend fun transitionTo(newState: RoomState) {
        when (state) {
            RoomState.Waiting -> {}
            RoomState.Countdown -> {
                if (newState is RoomState.Waiting) {
                    throw IllegalStateException("Invalid transition from Countdown to $newState")
                }
            }
            RoomState.Playing -> {
                if (newState is RoomState.Countdown) {
                    throw IllegalStateException("Invalid transition from Playing to $newState")
                }
            }
            RoomState.Closed -> {
                if (newState is RoomState.Countdown || newState is RoomState.Playing) {
                    throw IllegalStateException("Invalid transition from Closed to $newState")
                }
            }
        }
        state = newState
        onStateChanged(newState)
    }

    private suspend fun onStateChanged(newState: RoomState) {
        when (newState) {
            RoomState.Waiting -> TODO()
            RoomState.Countdown -> {
                countdownBeforeStart()
                transitionTo(RoomState.Playing)
            }
            RoomState.Playing -> {
                gameScope.launch {
                    game.transitionTo(GameState.Start)
                }
            }
            RoomState.Closed -> TODO()
        }
    }

    suspend fun playerAnswered(playerId: String, answer: Int) {
        if (roomEnumState != RoomEnumState.PLAYING) throw WrongCommandWrongTime()
        val lastRound = game.getLastRound()
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

    fun addPlayer(player: Player) {
        if (players.size >= game.maxPlayerCount()) throw TooMuchPlayersInRoom()
        if (roomEnumState != RoomEnumState.WAITING && roomEnumState != RoomEnumState.PAUSED) throw WrongCommandWrongTime()
        players.add(player.toDTO())
    }

    fun removePlayer(playerId: String) {
        players.removeIf { p -> p.id == playerId }
    }

    fun isAllPlayerReady(): Boolean {
        val notReadyPlayers = players.filter { player -> player.state == PlayerState.WAIT }.size
        return (notReadyPlayers == 0) && (game.maxPlayerCount() == players.size)
    }

    suspend fun broadcast(message: ServerSocketMessage) {
        println("Broadcasting message to room ${id}: $message")
        RoomBroadcastService.INSTANCE.broadcast(id, message)
    }

    suspend fun broadcastRoomState() {
        println("Broadcasting game state for room $id")

        val gameUpdate = ServerSocketMessage.RoomUpdate(
            players = players,
            state = roomEnumState,
        )
        broadcast(gameUpdate)
    }

    private suspend fun countdownBeforeStart() {
        println("Starting countdown for room $id")
        roomEnumState = RoomEnumState.COUNTDOWN
        broadcastRoomState()

        for (timeLeft in COUNTDOWN_TIME downTo 1) {
            delay(1000)
            val countdownTimeUpdate = ServerSocketMessage.CountdownTimeUpdate(remaining = timeLeft)
            broadcast(countdownTimeUpdate)
        }
        delay(1000)
    }
}