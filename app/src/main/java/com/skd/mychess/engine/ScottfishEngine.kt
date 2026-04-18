package com.skd.mychess.engine

import com.skd.mychess.model.ChessPiece
import com.skd.mychess.model.PieceType
import kotlin.random.Random

class ScottfishEngine(private val board: ChessBoard) {

    private val generator = MoveGenerator(board)

    // difficulty 1–10: maps to search depth and randomness
    fun findBestMove(isWhite: Boolean, difficulty: Int): Move? {
        val moves = generator.allLegalMoves(isWhite)
        if (moves.isEmpty()) return null

        return when {
            difficulty <= 2 -> moves.random()
            difficulty <= 4 -> bestGreedyMove(isWhite, moves, randomness = 0.4 - (difficulty - 3) * 0.15)
            difficulty <= 6 -> minimaxMove(isWhite, moves, depth = 2, randomness = 0.15 - (difficulty - 5) * 0.07)
            difficulty <= 8 -> minimaxMove(isWhite, moves, depth = 3, randomness = if (difficulty == 7) 0.05 else 0.0)
            else            -> minimaxMove(isWhite, moves, depth = 4, randomness = 0.0)
        }
    }

    private fun bestGreedyMove(isWhite: Boolean, moves: List<Move>, randomness: Double): Move {
        if (Random.nextDouble() < randomness) return moves.random()
        return moves.maxByOrNull { scoreMove(it, isWhite) } ?: moves.first()
    }

    private fun minimaxMove(isWhite: Boolean, moves: List<Move>, depth: Int, randomness: Double): Move {
        if (Random.nextDouble() < randomness) return moves.random()

        var bestScore = if (isWhite) Int.MIN_VALUE else Int.MAX_VALUE
        var bestMove = moves.first()

        for (move in moves) {
            val captured = applyMove(move)
            val score = minimax(!isWhite, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE)
            undoMove(move, captured)

            if (isWhite && score > bestScore || !isWhite && score < bestScore) {
                bestScore = score
                bestMove = move
            }
        }
        return bestMove
    }

    private fun minimax(isWhite: Boolean, depth: Int, alpha: Int, beta: Int): Int {
        if (depth == 0) return BoardEvaluator.evaluate(board)

        val moves = generator.allLegalMoves(isWhite)
        if (moves.isEmpty()) {
            return if (generator.isKingInCheck(isWhite)) {
                if (isWhite) -100000 else 100000
            } else 0 // stalemate
        }

        var a = alpha
        var b = beta

        if (isWhite) {
            var best = Int.MIN_VALUE
            for (move in moves) {
                val captured = applyMove(move)
                best = maxOf(best, minimax(false, depth - 1, a, b))
                undoMove(move, captured)
                a = maxOf(a, best)
                if (b <= a) break
            }
            return best
        } else {
            var best = Int.MAX_VALUE
            for (move in moves) {
                val captured = applyMove(move)
                best = minOf(best, minimax(true, depth - 1, a, b))
                undoMove(move, captured)
                b = minOf(b, best)
                if (b <= a) break
            }
            return best
        }
    }

    private fun applyMove(move: Move): ChessPiece? {
        val piece = board.getPiece(move.from) ?: return null
        val captured = board.getPiece(move.to)
        val finalPiece = if (move.promoteTo != null) {
            ChessPiece(move.promoteTo, piece.isWhite, piece.imageRes)
        } else piece
        board.setPiece(move.to, finalPiece)
        board.setPiece(move.from, null)
        return captured
    }

    private fun undoMove(move: Move, captured: ChessPiece?) {
        val piece = board.getPiece(move.to) ?: return
        val original = if (move.promoteTo != null) {
            ChessPiece(PieceType.PAWN, piece.isWhite, piece.imageRes)
        } else piece
        board.setPiece(move.from, original)
        if (captured != null) board.setPiece(move.to, captured) else board.setPiece(move.to, null)
    }

    private fun scoreMove(move: Move, isWhite: Boolean): Int {
        val captured = board.getPiece(move.to)
        val captureValue = when (captured?.type) {
            PieceType.QUEEN  -> 900
            PieceType.ROOK   -> 500
            PieceType.BISHOP -> 330
            PieceType.KNIGHT -> 320
            PieceType.PAWN   -> 100
            else -> 0
        }
        val centerBonus = if (move.to.row in 3..4 && move.to.col in 3..4) 10 else 0
        return captureValue + centerBonus
    }
}
