package com.skd.mychess.engine

import com.skd.mychess.model.ChessPiece
import com.skd.mychess.model.Position

class ChessBoard {

    private val board = mutableMapOf<Position, ChessPiece>()

    fun getPiece(pos: Position): ChessPiece? = board[pos]

    fun setPiece(pos: Position, piece: ChessPiece?) {
        if (piece == null) board.remove(pos)
        else board[pos] = piece
    }

    fun allPieces(): Map<Position, ChessPiece> = board.toMap()

    fun clear() = board.clear()
}
