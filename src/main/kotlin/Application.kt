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
import service.ActiveRoomsResponse
import service.GameService
import java.time.Duration
import java.util.*

private val gameService = GameService()

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
            call.respondText("Bayrak Quiz Oyun Sunucusu Çalışıyor!")
        }

        webSocket("/game") {
            val playerId = UUID.randomUUID().toString()
            
            try {
                gameService.registerPlayerSession(playerId, this)
                
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            gameService.handleMessage(playerId, text)
                        }
                        is Frame.Close -> {
                            gameService.handleDisconnect(playerId)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                println("Bağlantı hatası: ${e.message}")
                gameService.handleDisconnect(playerId)
            }
        }

        get("/rooms") {
            val rooms = gameService.getActiveRooms()
            call.respond(ActiveRoomsResponse(rooms))
        }
    }
}