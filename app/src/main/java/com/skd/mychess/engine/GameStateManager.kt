package com.skd.mychess.engine

import com.skd.mychess.model.Position

class GameStateManager {
    var whiteTurn = true
        private set

    // ── Castling rights ───────────────────────────────────────────────────────
    var whiteCanCastleKS = true   // king-side  (h-file rook)
    var whiteCanCastleQS = true   // queen-side (a-file rook)
    var blackCanCastleKS = true
    var blackCanCastleQS = true

    // ── En passant ────────────────────────────────────────────────────────────
    /** The square an opposing pawn can capture to (the skipped square). */
    var enPassantTarget: Position? = null

    // ── Turn control ─────────────────────────────────────────────────────────
    fun switchTurn() { whiteTurn = !whiteTurn }

    fun resetTurn() {
        whiteTurn        = true
        whiteCanCastleKS = true
        whiteCanCastleQS = true
        blackCanCastleKS = true
        blackCanCastleQS = true
        enPassantTarget  = null
    }

    fun setTurn(turn: Boolean) { whiteTurn = turn }

    // ── Castling helpers ──────────────────────────────────────────────────────
    fun canCastleKS(isWhite: Boolean) = if (isWhite) whiteCanCastleKS else blackCanCastleKS
    fun canCastleQS(isWhite: Boolean) = if (isWhite) whiteCanCastleQS else blackCanCastleQS

    fun revokeCastleKS(isWhite: Boolean) {
        if (isWhite) whiteCanCastleKS = false else blackCanCastleKS = false
    }
    fun revokeCastleQS(isWhite: Boolean) {
        if (isWhite) whiteCanCastleQS = false else blackCanCastleQS = false
    }
    fun revokeAllCastle(isWhite: Boolean) {
        if (isWhite) { whiteCanCastleKS = false; whiteCanCastleQS = false }
        else         { blackCanCastleKS = false; blackCanCastleQS = false }
    }
}
