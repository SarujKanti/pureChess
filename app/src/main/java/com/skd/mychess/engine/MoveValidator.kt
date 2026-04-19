package com.skd.mychess.engine

import com.skd.mychess.model.ChessPiece
import com.skd.mychess.model.PieceType
import com.skd.mychess.model.Position
import kotlin.math.abs

/**
 * Validates whether a single move is geometrically legal for a given piece,
 * taking into account castling rights and en-passant from [gameState] when provided.
 */
class MoveValidator(
    private val board: ChessBoard,
    private val gameState: GameStateManager? = null
) {

    fun isValidMove(from: Position, to: Position, piece: ChessPiece): Boolean {
        // Cannot capture own piece
        val target = board.getPiece(to)
        if (target != null && target.isWhite == piece.isWhite) return false

        return when (piece.type) {
            PieceType.PAWN   -> isPawnMove(from, to, piece)
            PieceType.ROOK   -> isStraightMove(from, to) && isPathClear(from, to)
            PieceType.BISHOP -> isDiagonalMove(from, to) && isPathClear(from, to)
            PieceType.QUEEN  -> (isStraightMove(from, to) || isDiagonalMove(from, to)) && isPathClear(from, to)
            PieceType.KNIGHT -> isKnightMove(from, to)
            PieceType.KING   -> isKingMove(from, to, piece)
        }
    }

    // ── King ─────────────────────────────────────────────────────────────────

    private fun isKingMove(from: Position, to: Position, piece: ChessPiece): Boolean {
        val rowDiff = abs(from.row - to.row)
        val colDiff = abs(from.col - to.col)
        // Normal one-step king move
        if (rowDiff <= 1 && colDiff <= 1) return true
        // Castling: same row, exactly 2 columns
        if (rowDiff == 0 && colDiff == 2) return isCastlingValid(from, to, piece)
        return false
    }

    /**
     * Checks that castling rights exist, the rook is in place, and the path is empty.
     * Does NOT check whether the king passes through check — that is [MoveGenerator.isMoveSafe]'s job.
     */
    private fun isCastlingValid(from: Position, to: Position, piece: ChessPiece): Boolean {
        if (gameState == null) return false
        val rank = if (piece.isWhite) 7 else 0
        if (from.row != rank || from.col != 4) return false

        val isKingSide = to.col > from.col
        val targetCol  = if (isKingSide) 6 else 2
        if (to.col != targetCol) return false

        if (isKingSide  && !gameState.canCastleKS(piece.isWhite)) return false
        if (!isKingSide && !gameState.canCastleQS(piece.isWhite)) return false

        val rookCol = if (isKingSide) 7 else 0
        val rook    = board.getPiece(Position(rank, rookCol)) ?: return false
        if (rook.type != PieceType.ROOK || rook.isWhite != piece.isWhite) return false

        // Every square between king and rook must be empty
        val clearRange = if (isKingSide) 5..6 else 1..3
        for (col in clearRange) {
            if (board.getPiece(Position(rank, col)) != null) return false
        }
        return true
    }

    // ── Pawn ─────────────────────────────────────────────────────────────────

    private fun isPawnMove(from: Position, to: Position, piece: ChessPiece): Boolean {
        val direction   = if (piece.isWhite) -1 else 1
        val startRow    = if (piece.isWhite) 6  else 1
        val rowDiff     = to.row - from.row
        val colDiff     = abs(to.col - from.col)
        val targetPiece = board.getPiece(to)

        // One step forward (square must be empty)
        if (colDiff == 0 && rowDiff == direction && targetPiece == null)
            return true

        // Two steps from starting row (both squares must be empty)
        if (colDiff == 0 && from.row == startRow && rowDiff == 2 * direction &&
            targetPiece == null &&
            board.getPiece(Position(from.row + direction, from.col)) == null)
            return true

        // Normal diagonal capture
        if (colDiff == 1 && rowDiff == direction &&
            targetPiece != null && targetPiece.isWhite != piece.isWhite)
            return true

        // En-passant capture (target square is empty, but matches the EP target)
        if (colDiff == 1 && rowDiff == direction && targetPiece == null) {
            val ep = gameState?.enPassantTarget ?: return false
            if (to == ep) return true
        }

        return false
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private fun isStraightMove(from: Position, to: Position) =
        from.row == to.row || from.col == to.col

    private fun isDiagonalMove(from: Position, to: Position) =
        abs(from.row - to.row) == abs(from.col - to.col)

    private fun isKnightMove(from: Position, to: Position) =
        abs(from.row - to.row) == 2 && abs(from.col - to.col) == 1 ||
        abs(from.row - to.row) == 1 && abs(from.col - to.col) == 2

    fun isPathClear(from: Position, to: Position): Boolean {
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
