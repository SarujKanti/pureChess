package com.skd.mychess.engine

import com.skd.mychess.model.ChessPiece
import com.skd.mychess.model.PieceType
import com.skd.mychess.model.Position
import kotlin.math.abs

data class Move(val from: Position, val to: Position, val promoteTo: PieceType? = null)

/**
 * Generates all legal moves for a side, and provides safety checks used by both
 * human-move validation and the AI engine.
 *
 * When [gameState] is supplied the generator uses explicit castling rights and
 * en-passant target (human-player path). When it is null the generator falls back
 * to an implicit board-state check (AI path).
 */
class MoveGenerator(
    private val board: ChessBoard,
    private val gameState: GameStateManager? = null
) {

    /** Public so GameActivity can reuse it for move highlighting. */
    val validator = MoveValidator(board, gameState)

    // ── Legal-move enumeration ────────────────────────────────────────────────

    fun allLegalMoves(isWhite: Boolean): List<Move> {
        val moves = mutableListOf<Move>()
        for ((pos, piece) in board.allPieces()) {
            if (piece.isWhite != isWhite) continue
            for (r in 0..7) {
                for (c in 0..7) {
                    val to = Position(r, c)
                    if (!validator.isValidMove(pos, to, piece)) continue
                    if (!isMoveSafe(pos, to, piece)) continue
                    if (piece.type == PieceType.PAWN && (r == 0 || r == 7)) {
                        // Auto-generate all promotion choices
                        listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
                            .forEach { moves.add(Move(pos, to, it)) }
                    } else {
                        moves.add(Move(pos, to))
                    }
                }
            }
        }
        // Castling for AI (implicit board-state check — no GameStateManager)
        if (gameState == null) addCastlingMovesImplicit(isWhite, moves)
        return moves
    }

    // ── AI castling (implicit) ────────────────────────────────────────────────

    private fun addCastlingMovesImplicit(isWhite: Boolean, moves: MutableList<Move>) {
        val rank    = if (isWhite) 7 else 0
        val kingPos = Position(rank, 4)
        val king    = board.getPiece(kingPos) ?: return
        if (king.type != PieceType.KING || king.isWhite != isWhite) return

        // King-side: f1/f8 and g1/g8 must be empty
        val ksRook = board.getPiece(Position(rank, 7))
        if (ksRook?.type == PieceType.ROOK && ksRook.isWhite == isWhite &&
            board.getPiece(Position(rank, 5)) == null &&
            board.getPiece(Position(rank, 6)) == null) {
            val target = Position(rank, 6)
            if (isMoveSafe(kingPos, target, king)) moves.add(Move(kingPos, target))
        }

        // Queen-side: b1/b8, c1/c8, d1/d8 must be empty
        val qsRook = board.getPiece(Position(rank, 0))
        if (qsRook?.type == PieceType.ROOK && qsRook.isWhite == isWhite &&
            board.getPiece(Position(rank, 1)) == null &&
            board.getPiece(Position(rank, 2)) == null &&
            board.getPiece(Position(rank, 3)) == null) {
            val target = Position(rank, 2)
            if (isMoveSafe(kingPos, target, king)) moves.add(Move(kingPos, target))
        }
    }

    // ── Safety check ─────────────────────────────────────────────────────────

    /**
     * Returns true if making [from]→[to] does NOT leave [piece]'s king in check.
     * Handles three special cases:
     *  - Castling (king must not start/pass through/land in check)
     *  - En-passant (remove the captured pawn before testing)
     *  - Normal moves
     */
    fun isMoveSafe(from: Position, to: Position, piece: ChessPiece): Boolean {
        val isCastling  = piece.type == PieceType.KING && abs(to.col - from.col) == 2
        val isEnPassant = piece.type == PieceType.PAWN &&
                          abs(to.col - from.col) == 1 &&
                          board.getPiece(to) == null

        return when {
            isCastling  -> castlingSafe(from, to, piece)
            isEnPassant -> enPassantSafe(from, to, piece)
            else        -> normalMoveSafe(from, to, piece)
        }
    }

    /** Full castling safety: not in check now, not passing through, not landing in check. */
    private fun castlingSafe(from: Position, to: Position, king: ChessPiece): Boolean {
        // 1. Must not be in check on the current square
        if (isKingInCheck(king.isWhite)) return false

        // 2. Must not pass through any checked square
        val stepCol = if (to.col > from.col) 1 else -1
        var col = from.col + stepCol
        while (col != to.col) {
            val intermediate = Position(from.row, col)
            board.setPiece(from, null)
            board.setPiece(intermediate, king)
            val inCheck = isKingInCheck(king.isWhite)
            board.setPiece(intermediate, null)
            board.setPiece(from, king)
            if (inCheck) return false
            col += stepCol
        }

        // 3. Must not land in check
        return normalMoveSafe(from, to, king)
    }

    /** En-passant: temporarily remove the captured pawn before checking. */
    private fun enPassantSafe(from: Position, to: Position, piece: ChessPiece): Boolean {
        val epPawnPos = Position(from.row, to.col)
        val epPawn    = board.getPiece(epPawnPos)
        board.setPiece(to, piece)
        board.setPiece(from, null)
        board.setPiece(epPawnPos, null)
        val safe = !isKingInCheck(piece.isWhite)
        board.setPiece(from, piece)
        board.setPiece(to, null)
        if (epPawn != null) board.setPiece(epPawnPos, epPawn)
        return safe
    }

    private fun normalMoveSafe(from: Position, to: Position, piece: ChessPiece): Boolean {
        val captured = board.getPiece(to)
        board.setPiece(to, piece)
        board.setPiece(from, null)
        val safe = !isKingInCheck(piece.isWhite)
        board.setPiece(from, piece)
        if (captured != null) board.setPiece(to, captured) else board.setPiece(to, null)
        return safe
    }

    // ── Check / mate / stalemate ──────────────────────────────────────────────

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
