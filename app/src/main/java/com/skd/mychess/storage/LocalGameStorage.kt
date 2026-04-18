package com.skd.mychess.storage

import android.content.Context
import android.content.SharedPreferences
import com.skd.mychess.model.GameMode

class LocalGameStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("PureChessGames", Context.MODE_PRIVATE)

    private fun keyTurn(mode: GameMode)        = "${mode.name}_turn"
    private fun keyPieces(mode: GameMode)      = "${mode.name}_pieces"
    private fun keyDifficulty()                = "COMPUTER_difficulty"
    private fun keyExists(mode: GameMode)      = "${mode.name}_exists"
    private fun keyPlayerColor(mode: GameMode) = "${mode.name}_player_white"

    fun saveGame(mode: GameMode, whiteTurn: Boolean, piecesData: String, difficulty: Int = 5, playerIsWhite: Boolean = true) {
        prefs.edit()
            .putBoolean(keyTurn(mode), whiteTurn)
            .putString(keyPieces(mode), piecesData)
            .putBoolean(keyExists(mode), true)
            .putBoolean(keyPlayerColor(mode), playerIsWhite)
            .apply()
        if (mode == GameMode.COMPUTER) {
            prefs.edit().putInt(keyDifficulty(), difficulty).apply()
        }
    }

    fun hasSavedGame(mode: GameMode): Boolean =
        prefs.getBoolean(keyExists(mode), false) &&
        prefs.getString(keyPieces(mode), null)?.isNotEmpty() == true

    fun loadTurn(mode: GameMode): Boolean = prefs.getBoolean(keyTurn(mode), true)

    fun loadPieces(mode: GameMode): String? = prefs.getString(keyPieces(mode), null)

    fun loadDifficulty(): Int = prefs.getInt(keyDifficulty(), 5)

    fun loadPlayerColor(mode: GameMode): Boolean = prefs.getBoolean(keyPlayerColor(mode), true)

    fun clearGame(mode: GameMode) {
        prefs.edit()
            .remove(keyTurn(mode))
            .remove(keyPieces(mode))
            .putBoolean(keyExists(mode), false)
            .apply()
    }
}
