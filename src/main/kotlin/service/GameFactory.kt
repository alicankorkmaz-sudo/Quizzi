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

    fun createGame(id: String, categoryId: Int, type: String): Game {
        return when(type) {
            "ResistanceGame" -> ResistanceGame(id, categoryId)
            else -> ResistanceGame(id, categoryId)
        }
    }
}