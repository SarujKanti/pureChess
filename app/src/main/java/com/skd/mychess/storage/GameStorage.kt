package com.skd.mychess.storage

import android.content.SharedPreferences

class GameStorage(private val prefs: SharedPreferences) {

    fun save(turn: Boolean, data: String) {
        prefs.edit()
            .putBoolean("turn", turn)
            .putString("pieces", data)
            .apply()
    }

    fun loadTurn(): Boolean = prefs.getBoolean("turn", true)
    fun loadPieces(): String? = prefs.getString("pieces", null)
}
