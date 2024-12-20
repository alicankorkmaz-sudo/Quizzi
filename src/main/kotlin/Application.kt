import exception.SocketCloseError
import handler.MessageHandler
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import router.gameRoutes
import router.playerRoutes
import router.roomRoutes
import service.PlayerManagerService
import service.SessionManagerService
import java.time.Duration

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toInt() ?: 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        get("/") {
            call.respondText("models.Flag Quiz Game Server Running!")
        }

        roomRoutes()
        playerRoutes()
        gameRoutes()

        webSocket("/game") {
            val playerId = call.parameters["playerId"]//call.request.headers["playerId"]

            if (playerId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing playerId"))
                return@webSocket
            }
            println("New WebSocket connection: $playerId")
            try {
                PlayerManagerService.INSTANCE.getPlayer(playerId)
                SessionManagerService.INSTANCE.addPlayerToSession(playerId, this)

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            MessageHandler.INSTANCE.handleMessage(playerId, text)
                        }

                        is Frame.Close -> {}

                        else -> {}
                    }
                }
            } catch (e: SocketCloseError) {
                println("Error in WebSocket connection: ${e.message}")
                close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, e.message))
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unexpected Error!"))
            } finally {
                println("WebSocket connection terminated for player $playerId")
                MessageHandler.INSTANCE.handleDisconnect(playerId)
            }
        }
    }
}