package model

import domain.RoomEvent
import exception.RoomIsEmpty
import exception.TooMuchPlayersInRoom
import exception.WrongCommandWrongTime
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import response.ServerSocketMessage
import service.RoomBroadcastService
import service.RoomManagerService
import state.GameState
import state.PlayerState
import state.RoomState
import java.util.*

@Serializable
data class GameRoom(
    val id: String,
    val name: String,
    val game: Game,
    private val players: MutableSet<PlayerInRoom> = Collections.synchronizedSet(mutableSetOf())
) {
    companion object {
        const val COUNTDOWN_TIME = 3L
    }

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

            RoomState.Pausing -> {
                if (newState !is RoomState.Countdown && newState !is RoomState.Closing) {
                    throw IllegalStateException("Invalid transition from Playing to $newState")
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
                    throw WrongCommandWrongTime()
                }
            }

            RoomState.Pausing -> {}
            RoomState.Playing -> {
                if (event !is RoomEvent.Disconnected) {
                    throw WrongCommandWrongTime()
                }
            }

            RoomState.Closing -> {
                throw WrongCommandWrongTime()
            }

        }
        onProcessEvent(event)
    }

    private suspend fun onStateChanged(newState: RoomState) {
        broadcastRoomState()
        when (newState) {
            RoomState.Waiting -> {}
            RoomState.Countdown -> {
                countdownBeforeStart()
                transitionTo(RoomState.Playing)
            }

            RoomState.Pausing -> {
                game.transitionTo(GameState.Pause)
            }

            RoomState.Playing -> {
                gameScope.launch {
                    game.transitionTo(GameState.Playing)
                }
            }

            RoomState.Closing -> {
                game.transitionTo(GameState.Over)
                RoomManagerService.INSTANCE.cleanupRoom(this)
            }
        }
    }

    private suspend fun onProcessEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.Created -> {
                addPlayer(event.player)
                broadcastRoomState()
            }

            is RoomEvent.Joined -> {
                addPlayer(event.player)
                broadcastRoomState()
            }

            RoomEvent.Rejoined -> {}
            is RoomEvent.Ready -> {
                playerReady(event.playerId)
                if (isAllPlayerReady()) {
                    transitionTo(RoomState.Countdown)
                }
            }

            is RoomEvent.Disconnected -> {
                removePlayer(event.playerId)

                val disconnectMessage = ServerSocketMessage.PlayerDisconnected(
                    playerId = event.playerId
                )
                broadcast(disconnectMessage)

                if (players.isEmpty()) {
                    transitionTo(RoomState.Closing)
                    throw RoomIsEmpty(id)
                }
                transitionTo(RoomState.Pausing)
            }
        }
    }

    /////////////////////////////////

    fun getPlayerCount(): Int = players.size

    fun getPlayerNames(): List<String> = players.map { it.name }

    fun getPlayers(): Set<PlayerInRoom> = players

    /////////////////////////////////

    private suspend fun broadcast(message: ServerSocketMessage) {
        println("Broadcasting message to room ${id}: $message")
        RoomBroadcastService.INSTANCE.broadcast(id, message)
    }

    private suspend fun broadcastRoomState() {
        println("Broadcasting game state for room $id")

        val gameUpdate = ServerSocketMessage.RoomUpdate(
            players = players.map { it.toDTO() },
            state = state,
        )
        broadcast(gameUpdate)
    }

    /////////////////////////////////

    private fun addPlayer(player: Player) {
        if (players.size >= game.maxPlayerCount()) throw TooMuchPlayersInRoom()
        val index = players.size
        players.add(player.toPlayerInRoom(index))
        game.players.add(player.toPlayerInGame(index))
    }

    private fun removePlayer(playerId: String) {
        players.removeIf { p -> p.id == playerId }
        game.players.removeIf { p -> p.id == playerId }
    }

    private fun isAllPlayerReady(): Boolean {
        val notReadyPlayers = players.filter { player -> player.state == PlayerState.WAIT }.size
        return (notReadyPlayers == 0) && (game.maxPlayerCount() == players.size)
    }

    private fun playerReady(playerId: String) {
        players
            .filter { player -> player.id == playerId }
            .forEach { player -> player.state = PlayerState.READY }
    }

    private suspend fun countdownBeforeStart() {
        println("Starting countdown for room $id")
        for (timeLeft in COUNTDOWN_TIME downTo 1) {
            delay(1000)
            val countdownTimeUpdate = ServerSocketMessage.CountdownTimeUpdate(remaining = timeLeft)
            broadcast(countdownTimeUpdate)
        }
        delay(1000)
    }
}