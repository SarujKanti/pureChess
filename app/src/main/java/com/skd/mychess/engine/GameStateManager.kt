package com.skd.mychess.engine

class GameStateManager {
    var whiteTurn = true
        private set

    fun switchTurn() {
        whiteTurn = !whiteTurn
    }

    fun resetTurn() {
        whiteTurn = true
    }

    fun setTurn(turn: Boolean) {
        whiteTurn = turn
    }

}
