package router

/**
 * @author guvencenanguvenal
 */
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import response.ActiveRoomsResponse
import service.RoomManagerService

fun Route.roomRoutes() {
    route("/api/room") {
        get("/all") {
            val rooms = RoomManagerService.INSTANCE.getActiveRooms()
            call.respond(ActiveRoomsResponse(rooms))
        }
    }
}