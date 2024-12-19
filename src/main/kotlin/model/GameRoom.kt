package model

import domain.RoomEvent
import dto.PlayerDTO
import enums.RoomEnumState
import exception.TooMuchPlayersInRoom
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import response.ServerSocketMessage
import service.RoomBroadcastService
import state.GameState
import state.PlayerState
import state.RoomState
import java.util.*

@Serializable
data class GameRoom(
    val id: String,
    val name: String,
    val game: Game,
    val players: MutableList<PlayerDTO> = Collections.synchronizedList(mutableListOf()),
    var roomEnumState: RoomEnumState = RoomEnumState.WAITING,
) {
    companion object{
        const val COUNTDOWN_TIME = 3L
    }

    private val gameScope = CoroutineScope(Dispatchers.Default + Job())

    private var state: RoomState = RoomState.Waiting

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

            RoomState.Closing -> {
                if (newState is RoomState.Countdown || newState is RoomState.Playing) {
                    throw IllegalStateException("Invalid transition from Closed to $newState")
                }
            }
        }
        state = newState
        onStateChanged(newState)
    }

    suspend fun handleEvent(event: RoomEvent) {
        when (state) {
            RoomState.Waiting -> {}
            RoomState.Countdown -> {
                if (event !is RoomEvent.Disconnected) {
                    throw IllegalStateException("Invalid event on $state")
                }
            }

            RoomState.Playing -> {
                if (event !is RoomEvent.Disconnected) {
                    throw IllegalStateException("Invalid event on $state")
                }
            }

            RoomState.Closing -> {
                throw IllegalStateException("Invalid event on $state")
            }
        }
        onProcessEvent(event)
    }

    private suspend fun onStateChanged(newState: RoomState) {
        when (newState) {
            RoomState.Waiting -> {}
            RoomState.Countdown -> {
                countdownBeforeStart()
                transitionTo(RoomState.Playing)
            }

            RoomState.Playing -> {
                gameScope.launch {
                    game.transitionTo(GameState.Playing)
                }
            }

            RoomState.Closing -> {}
        }
        broadcastRoomState()
    }

    private suspend fun onProcessEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.Joined -> {
                addPlayer(event.player)
                broadcastRoomState()
            }

            RoomEvent.Rejoined -> {}
            is RoomEvent.Ready -> {
                playerReady(event.playerId)
                if (isAllPlayerReady()) {
                    transitionTo(RoomState.Playing)
                }
            }

            is RoomEvent.Disconnected -> {
                removePlayer(event.playerId)
                transitionTo(RoomState.Waiting)
            }
        }
    }

    /////////////////////////////////

    fun addPlayer(player: Player) {
        if (players.size >= game.maxPlayerCount()) throw TooMuchPlayersInRoom()
        players.add(player.toDTO())
    }

    fun removePlayer(playerId: String) {
        players.removeIf { p -> p.id == playerId }
    }

    fun isAllPlayerReady(): Boolean {
        val notReadyPlayers = players.filter { player -> player.state == PlayerState.WAIT }.size
        return (notReadyPlayers == 0) && (game.maxPlayerCount() == players.size)
    }

    fun playerReady(playerId: String) {
        players
            .filter { player -> player.id == playerId }
            .forEach { player -> player.state = PlayerState.READY }
    }

    /////////////////////////////////

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

    /////////////////////////////////

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