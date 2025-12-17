package com.skd.mychess.model

enum class PieceType {
    PAWN, ROOK, KNIGHT, BISHOP, QUEEN, KING
}

data class ChessPiece(
    val type: PieceType,
    val isWhite: Boolean,
    val imageRes: Int
)
