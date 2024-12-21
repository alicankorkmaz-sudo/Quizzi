package service

import model.Game
import model.ResistToTimeGame
import model.ResistanceGame

/**
 * @author guvencenanguvenal
 */
class GameFactory private constructor() {
    companion object {
        val INSTANCE: GameFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { GameFactory() }

        fun getAllGameTypes(): Set<String> {
            return mutableSetOf("ResistanceGame")
        }
    }

    fun createGame(id: String, categoryId: Int, type: String, roomId: String): Game {
        return when(type) {
            "ResistanceGame" -> ResistanceGame(id, categoryId, roomId)
            "ResistToTimeGame" -> ResistToTimeGame(id, categoryId, roomId)
            else -> ResistanceGame(id, categoryId, roomId)
        }
    }
}