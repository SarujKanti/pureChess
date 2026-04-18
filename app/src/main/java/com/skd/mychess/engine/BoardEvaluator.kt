package com.skd.mychess.engine

import com.skd.mychess.model.PieceType

object BoardEvaluator {

    private val PIECE_VALUE = mapOf(
        PieceType.PAWN   to 100,
        PieceType.KNIGHT to 320,
        PieceType.BISHOP to 330,
        PieceType.ROOK   to 500,
        PieceType.QUEEN  to 900,
        PieceType.KING   to 20000
    )

    // Piece-square tables (from black's perspective; flip row for white)
    private val PAWN_TABLE = arrayOf(
        intArrayOf( 0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(50, 50, 50, 50, 50, 50, 50, 50),
        intArrayOf(10, 10, 20, 30, 30, 20, 10, 10),
        intArrayOf( 5,  5, 10, 25, 25, 10,  5,  5),
        intArrayOf( 0,  0,  0, 20, 20,  0,  0,  0),
        intArrayOf( 5, -5,-10,  0,  0,-10, -5,  5),
        intArrayOf( 5, 10, 10,-20,-20, 10, 10,  5),
        intArrayOf( 0,  0,  0,  0,  0,  0,  0,  0)
    )

    private val KNIGHT_TABLE = arrayOf(
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50),
        intArrayOf(-40,-20,  0,  0,  0,  0,-20,-40),
        intArrayOf(-30,  0, 10, 15, 15, 10,  0,-30),
        intArrayOf(-30,  5, 15, 20, 20, 15,  5,-30),
        intArrayOf(-30,  0, 15, 20, 20, 15,  0,-30),
        intArrayOf(-30,  5, 10, 15, 15, 10,  5,-30),
        intArrayOf(-40,-20,  0,  5,  5,  0,-20,-40),
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50)
    )

    private val BISHOP_TABLE = arrayOf(
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20),
        intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
        intArrayOf(-10,  0,  5, 10, 10,  5,  0,-10),
        intArrayOf(-10,  5,  5, 10, 10,  5,  5,-10),
        intArrayOf(-10,  0, 10, 10, 10, 10,  0,-10),
        intArrayOf(-10, 10, 10, 10, 10, 10, 10,-10),
        intArrayOf(-10,  5,  0,  0,  0,  0,  5,-10),
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20)
    )

    private val ROOK_TABLE = arrayOf(
        intArrayOf( 0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf( 5, 10, 10, 10, 10, 10, 10,  5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf( 0,  0,  0,  5,  5,  0,  0,  0)
    )

    private val QUEEN_TABLE = arrayOf(
        intArrayOf(-20,-10,-10, -5, -5,-10,-10,-20),
        intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
        intArrayOf(-10,  0,  5,  5,  5,  5,  0,-10),
        intArrayOf( -5,  0,  5,  5,  5,  5,  0, -5),
        intArrayOf(  0,  0,  5,  5,  5,  5,  0, -5),
        intArrayOf(-10,  5,  5,  5,  5,  5,  0,-10),
        intArrayOf(-10,  0,  5,  0,  0,  0,  0,-10),
        intArrayOf(-20,-10,-10, -5, -5,-10,-10,-20)
    )

    private val KING_TABLE = arrayOf(
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-20,-30,-30,-40,-40,-30,-30,-20),
        intArrayOf(-10,-20,-20,-20,-20,-20,-20,-10),
        intArrayOf( 20, 20,  0,  0,  0,  0, 20, 20),
        intArrayOf( 20, 30, 10,  0,  0, 10, 30, 20)
    )

    fun evaluate(board: ChessBoard): Int {
        var score = 0
        for ((pos, piece) in board.allPieces()) {
            val row = if (piece.isWhite) 7 - pos.row else pos.row
            val tableBonus = when (piece.type) {
                PieceType.PAWN   -> PAWN_TABLE[row][pos.col]
                PieceType.KNIGHT -> KNIGHT_TABLE[row][pos.col]
                PieceType.BISHOP -> BISHOP_TABLE[row][pos.col]
                PieceType.ROOK   -> ROOK_TABLE[row][pos.col]
                PieceType.QUEEN  -> QUEEN_TABLE[row][pos.col]
                PieceType.KING   -> KING_TABLE[row][pos.col]
            }
            val value = (PIECE_VALUE[piece.type] ?: 0) + tableBonus
            score += if (piece.isWhite) value else -value
        }
        return score
    }
}
