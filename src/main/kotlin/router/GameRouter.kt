package router

/**
 * @author guvencenanguvenal
 */
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import response.ActiveRoomsResponse
import response.AllCategoriesResponse
import response.AllGameTypesResponse
import service.CategoryService
import service.GameFactory
import service.RoomManagerService

fun Route.gameRoutes() {
    route("/api/game") {
        get("/all") {
            val gameTypes = GameFactory.getAllGameTypes()
            call.respond(AllGameTypesResponse(gameTypes))
        }

        get("/category/all") {
            val categories = CategoryService.getAllCategories()
            call.respond(AllCategoriesResponse(categories))
        }
    }
}