package service

import model.Game
import model.ResistanceGame

/**
 * @author guvencenanguvenal
 */
class GameFactory private constructor() {
    companion object {
        val INSTANCE: GameFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { GameFactory() }
    }

    fun createGame(id: String, categoryId: Int, type: String, roomId: String): Game {
        return when(type) {
            "ResistanceGame" -> ResistanceGame(id, categoryId, roomId)
            else -> ResistanceGame(id, categoryId, roomId)
        }
    }

    class GameType {
        companion object {
            const val RESISTANCE_GAME = "ResistanceGame"
        }
    }

    class CategoryType {
        companion object {
            const val FLAGS = 1
        }
    }
}