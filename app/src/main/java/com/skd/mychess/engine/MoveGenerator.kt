package com.skd.mychess.engine

import com.skd.mychess.model.ChessPiece
import com.skd.mychess.model.PieceType
import com.skd.mychess.model.Position

data class Move(val from: Position, val to: Position, val promoteTo: PieceType? = null)

class MoveGenerator(private val board: ChessBoard) {

    private val validator = MoveValidator(board)

    fun allLegalMoves(isWhite: Boolean): List<Move> {
        val moves = mutableListOf<Move>()
        for ((pos, piece) in board.allPieces()) {
            if (piece.isWhite != isWhite) continue
            for (r in 0..7) {
                for (c in 0..7) {
                    val to = Position(r, c)
                    if (validator.isValidMove(pos, to, piece) && isMoveSafe(pos, to, piece)) {
                        if (piece.type == PieceType.PAWN && (r == 0 || r == 7)) {
                            listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
                                .forEach { moves.add(Move(pos, to, it)) }
                        } else {
                            moves.add(Move(pos, to))
                        }
                    }
                }
            }
        }
        return moves
    }

    fun isMoveSafe(from: Position, to: Position, piece: ChessPiece): Boolean {
        val captured = board.getPiece(to)
        board.setPiece(to, piece)
        board.setPiece(from, null)
        val safe = !isKingInCheck(piece.isWhite)
        board.setPiece(from, piece)
        if (captured != null) board.setPiece(to, captured) else board.setPiece(to, null)
        return safe
    }

    fun isKingInCheck(isWhite: Boolean): Boolean {
        val kingPos = board.allPieces().entries
            .firstOrNull { it.value.type == PieceType.KING && it.value.isWhite == isWhite }
            ?.key ?: return false
        for ((pos, piece) in board.allPieces()) {
            if (piece.isWhite != isWhite && validator.isValidMove(pos, kingPos, piece)) return true
        }
        return false
    }

    fun isCheckmate(isWhite: Boolean) = isKingInCheck(isWhite) && allLegalMoves(isWhite).isEmpty()
    fun isStalemate(isWhite: Boolean) = !isKingInCheck(isWhite) && allLegalMoves(isWhite).isEmpty()
}
