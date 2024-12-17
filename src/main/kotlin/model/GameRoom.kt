package model

import dto.PlayerDTO
import enums.PlayerState
import enums.RoomState
import enums.RoomStateEnum
import enums.RoundState
import exception.TooMuchPlayersInRoom
import exception.WrongCommandWrongTime
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import response.ServerSocketMessage
import service.RoomManagerService.Companion.COUNTDOWN_TIME
import service.SessionManagerService
import java.util.*

@Serializable
data class GameRoom(
    val id: String,
    val name: String,
    val game: Game,
    val players: MutableList<PlayerDTO> = Collections.synchronizedList(mutableListOf()),
    var roomStateEnum: RoomStateEnum = RoomStateEnum.WAITING,
) {
    private val gameScope = CoroutineScope(Dispatchers.Default + Job())

    var state: RoomState = RoomState.Waiting

    fun transitionTo(newState: RoomState) {
        gameScope.launch {
            when (state) {
                RoomState.Waiting -> TODO()
                RoomState.Countdown -> {
                    println("Starting countdown for room $id")
                    countdownBeforeStart()
                }
                RoomState.Playing -> {
                    println("Starting actual game for room $id")
                    processRound()
                }
                RoomState.Paused -> TODO()
                RoomState.Closed -> {

                }
            }
            state = newState
            onChanged(newState)
        }
    }

    private fun onChanged(newState: RoomState) {
        when (newState) {
            RoomState.Waiting -> TODO()
            RoomState.Countdown -> {
                transitionTo(RoomState.Playing)
            }
            RoomState.Playing -> TODO()
            RoomState.Paused -> TODO()
            RoomState.Closed -> TODO()
        }
    }

    fun addPlayer(player: Player) {
        if (players.size >= game.maxPlayerCount()) throw TooMuchPlayersInRoom()
        if (roomStateEnum != RoomStateEnum.WAITING && roomStateEnum != RoomStateEnum.PAUSED) throw WrongCommandWrongTime()
        players.add(player.toDTO())
    }

    fun removePlayer(playerId: String) {
        players.removeIf { p -> p.id == playerId }
    }

    fun isAllPlayerReady(): Boolean {
        val notReadyPlayers = players.filter { player -> player.state == PlayerState.WAIT }.size
        return (notReadyPlayers == 0) && (game.maxPlayerCount() == players.size)
    }

    fun playerAnswered(player: Player, answer: Int) {
        game.getLastRound().playerAnswered(player, answer)
    }

    suspend fun broadcast(message: ServerSocketMessage) {
        println("Broadcasting message to room ${id}: $message")
        val playerIds = players.map(PlayerDTO::id).toMutableList()
        SessionManagerService.INSTANCE.broadcastToPlayers(playerIds, message)
    }

    suspend fun broadcastRoomState() {
        println("Broadcasting game state for room $id")

        val gameUpdate = ServerSocketMessage.RoomUpdate(
            players = players,
            state = roomStateEnum,
        )
        broadcast(gameUpdate)
    }

    private suspend fun countdownBeforeStart() {
        broadcastRoomState()

        for (timeLeft in COUNTDOWN_TIME downTo 1) {
            delay(1000)
            val countdownTimeUpdate = ServerSocketMessage.CountdownTimeUpdate(remaining = timeLeft)
            broadcast(countdownTimeUpdate)
        }
        delay(1000)
    }

    private suspend fun processRound() {
        while (state == RoomState.Playing) {
            delay(2000)
            val round = game.nextRound()
            val roundStarted = ServerSocketMessage.RoundStarted(
                roundNumber = round.number,
                timeRemaining = game.getRoundTime(),
                currentQuestion = game.getLastRound().question.toDTO()
            )
            broadcast(roundStarted)
            for (timeLeft in game.getRoundTime() - 1 downTo 1) {
                delay(1000)
                val timeUpdate = ServerSocketMessage.TimeUpdate(remaining = timeLeft)
                broadcast(timeUpdate)
            }
            delay(1000)
            // Süre doldu mesajı
            val timeUpMessage = ServerSocketMessage.TimeUp(correctAnswer = game.getLastRound().question.answer)
            broadcast(timeUpMessage)

            val resistanceGame = game as ResistanceGame

            val roundEnded = ServerSocketMessage.RoundEnded(
                cursorPosition = resistanceGame.cursorPosition,
                correctAnswer = resistanceGame.getLastRound().question.answer,
                winnerPlayerId = null
            )
            broadcast(roundEnded)
        }
    }
}