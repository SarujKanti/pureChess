package com.skd.mychess.engine

import com.skd.mychess.model.ChessPiece
import com.skd.mychess.model.PieceType
import com.skd.mychess.model.Position
import kotlin.math.abs

class MoveValidator(private val board: ChessBoard) {

    fun isValidMove(from: Position, to: Position, piece: ChessPiece): Boolean {
        val target = board.getPiece(to)
        if (target != null && target.isWhite == piece.isWhite) return false

        return when (piece.type) {
            PieceType.PAWN -> isPawnMove(from, to, piece)
            PieceType.ROOK -> isStraightMove(from, to) && isPathClear(from, to)
            PieceType.BISHOP -> isDiagonalMove(from, to) && isPathClear(from, to)
            PieceType.QUEEN -> (isStraightMove(from, to) || isDiagonalMove(from, to)) && isPathClear(from, to)
            PieceType.KNIGHT -> isKnightMove(from, to)
            PieceType.KING -> abs(from.row - to.row) <= 1 && abs(from.col - to.col) <= 1
        }
    }

    private fun isPawnMove(from: Position, to: Position, piece: ChessPiece): Boolean {

        val direction = if (piece.isWhite) -1 else 1
        val startRow = if (piece.isWhite) 6 else 1

        val rowDiff = to.row - from.row
        val colDiff = kotlin.math.abs(to.col - from.col)

        val targetPiece = board.getPiece(to)

        if (colDiff == 0 &&
            rowDiff == direction &&
            targetPiece == null
        ) {
            return true
        }

        if (colDiff == 0 &&
            from.row == startRow &&
            rowDiff == 2 * direction &&
            targetPiece == null &&
            board.getPiece(Position(from.row + direction, from.col)) == null
        ) {
            return true
        }

        if (colDiff == 1 &&
            rowDiff == direction &&
            targetPiece != null &&
            targetPiece.isWhite != piece.isWhite
        ) {
            return true
        }

        return false
    }


    private fun isStraightMove(from: Position, to: Position) =
        from.row == to.row || from.col == to.col

    private fun isDiagonalMove(from: Position, to: Position) =
        abs(from.row - to.row) == abs(from.col - to.col)

    private fun isKnightMove(from: Position, to: Position) =
        abs(from.row - to.row) == 2 && abs(from.col - to.col) == 1 ||
                abs(from.row - to.row) == 1 && abs(from.col - to.col) == 2

    private fun isPathClear(from: Position, to: Position): Boolean {
        val stepRow = Integer.signum(to.row - from.row)
        val stepCol = Integer.signum(to.col - from.col)

        var r = from.row + stepRow
        var c = from.col + stepCol

        while (r != to.row || c != to.col) {
            if (board.getPiece(Position(r, c)) != null) return false
            r += stepRow
            c += stepCol
        }
        return true
    }
}
