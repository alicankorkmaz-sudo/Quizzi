package router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import request.CreatePlayerRequest
import request.LoginPlayerRequest
import response.PlayerResponse
import service.PlayerManagerService

/**
 * @author guvencenanguvenal
 */

fun Route.playerRoutes() {
    route("/api/player") {
        post("/create") {
            try {
                val request = call.receive<CreatePlayerRequest>()
                val player = PlayerManagerService.INSTANCE.createPlayer(request.name, request.avatarUrl)
                call.respond(HttpStatusCode.Created, PlayerResponse(player))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, e.message!!)
            }
        }

        post("/login") {
            try {
                val request = call.receive<LoginPlayerRequest>()
                val player = PlayerManagerService.INSTANCE.getPlayer(request.id)
                call.respond(HttpStatusCode.Created, PlayerResponse(player!!))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request data")
            }
        }
    }
}