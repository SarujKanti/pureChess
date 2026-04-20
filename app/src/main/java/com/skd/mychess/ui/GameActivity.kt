package com.skd.mychess.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.skd.mychess.R
import com.skd.mychess.engine.*
import com.skd.mychess.model.ChessPiece
import com.skd.mychess.model.GameMode
import com.skd.mychess.model.PieceType
import com.skd.mychess.model.Position
import com.skd.mychess.engine.SoundManager
import com.skd.mychess.storage.LocalGameStorage
import com.skd.mychess.storage.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE         = "game_mode"
        const val EXTRA_DIFFICULTY   = "difficulty"
        const val EXTRA_RESUME       = "resume"
        const val EXTRA_PLAYER_WHITE = "player_is_white"
        const val EXTRA_P1_NAME      = "p1_name"
        const val EXTRA_P2_NAME      = "p2_name"

        // Fixed highlight colours (independent of board theme)
        private val COLOR_SELECTED   = Color.parseColor("#BBD4AF37")
        private val COLOR_CHECK      = Color.parseColor("#CCFF3A3A")
        private val DOT_COLOR        = Color.parseColor("#55000000")
        private val RING_COLOR       = Color.parseColor("#CC000000")
        private val COLOR_LAST_LIGHT = Color.parseColor("#CCF6F644")
        private val COLOR_LAST_DARK  = Color.parseColor("#CCBACA2B")
    }

    // Board theme colours — loaded from SettingsManager in onCreate
    private var COLOR_LIGHT = Color.parseColor("#F0D9B5")
    private var COLOR_DARK  = Color.parseColor("#B58863")

    // ─── Engine ──────────────────────────────────────────────────────────────
    private val board     = ChessBoard()
    private val gameState = GameStateManager()
    private lateinit var generator: MoveGenerator   // MoveGenerator(board, gameState)
    private lateinit var ai: ScottfishEngine

    // ─── Game state ──────────────────────────────────────────────────────────
    private lateinit var mode: GameMode
    private var difficulty       = 5
    private var playerIsWhite    = true
    private var boardFlipped     = false
    private var computerThinking = false
    private var isAbandoned      = false   // true → onPause must NOT re-save
    private var p1Name           = "Player 1"
    private var p2Name           = "Player 2"

    // Last move tracking (board coordinates)
    private var lastMoveFrom:   Position?  = null
    private var lastMoveTo:     Position?  = null
    private var lastMovedPiece: PieceType? = null

    // Move history  ("1. e4  e5  2. Nf3  Nc6 …")
    // Each entry is one half-move in order; index 0 = first white move, etc.
    private val moveLog = mutableListOf<String>()

    // ─── UI ──────────────────────────────────────────────────────────────────
    private lateinit var chessBoard: GridLayout
    private var cellSize = 0
    private val cells    = Array(8) { Array<FrameLayout?>(8) { null } }
    private var selectedPos: Position? = null

    private lateinit var topPlayerCard:      LinearLayout
    private lateinit var bottomPlayerCard:   LinearLayout
    private lateinit var txtTopName:         TextView
    private lateinit var txtBottomName:      TextView
    private lateinit var txtTopCaptured:     TextView
    private lateinit var txtBottomCaptured:  TextView
    private lateinit var dotTopTurn:         View
    private lateinit var dotBottomTurn:      View
    private lateinit var txtTopTurnLabel:    TextView
    private lateinit var txtBottomTurnLabel: TextView
    private lateinit var txtStatus:          TextView
    private lateinit var txtDiffBadge:       TextView
    private lateinit var txtModeLabel:       TextView
    private lateinit var imgTopPlayer:       TextView
    private lateinit var imgBottomPlayer:    TextView
    private lateinit var moveHistoryScroll:  HorizontalScrollView
    private lateinit var txtMoveHistory:     TextView

    // ─── Settings & Sound ────────────────────────────────────────────────────
    private lateinit var settings: SettingsManager
    private lateinit var sound:    SoundManager

    // ─── Storage ─────────────────────────────────────────────────────────────
    private lateinit var storage: LocalGameStorage

    // ─── Captured piece lists ─────────────────────────────────────────────────
    private val capturedByWhite = mutableListOf<PieceType>()
    private val capturedByBlack = mutableListOf<PieceType>()

    // =========================================================================
    // Coordinate helpers
    // =========================================================================

    private fun dRow(br: Int) = if (boardFlipped) 7 - br else br
    private fun dCol(bc: Int) = if (boardFlipped) 7 - bc else bc
    private fun bRow(vr: Int) = if (boardFlipped) 7 - vr else vr
    private fun bCol(vc: Int) = if (boardFlipped) 7 - vc else vc

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        mode          = GameMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: GameMode.FRIEND.name)
        difficulty    = intent.getIntExtra(EXTRA_DIFFICULTY, 5)
        val resume    = intent.getBooleanExtra(EXTRA_RESUME, false)
        playerIsWhite = intent.getBooleanExtra(EXTRA_PLAYER_WHITE, true)
        p1Name        = intent.getStringExtra(EXTRA_P1_NAME) ?: "Player 1"
        p2Name        = intent.getStringExtra(EXTRA_P2_NAME) ?: "Player 2"

        storage   = LocalGameStorage(this)
        settings  = SettingsManager(this)
        sound     = SoundManager(settings)
        generator = MoveGenerator(board, gameState)
        ai        = ScottfishEngine(board)

        // Load board theme colours from settings
        val (_, light, dark) = SettingsManager.BOARD_THEMES[settings.boardTheme]
        COLOR_LIGHT = light
        COLOR_DARK  = dark

        bindViews()
        setupTopBar()

        if (resume && storage.hasSavedGame(mode)) {
            playerIsWhite = storage.loadPlayerColor(mode)
            loadGame()
        } else {
            startNewGame()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!computerThinking && !isAbandoned) saveGame()
    }

    // =========================================================================
    // View binding
    // =========================================================================

    private fun bindViews() {
        chessBoard         = findViewById(R.id.chessBoard)
        topPlayerCard      = findViewById(R.id.topPlayerCard)
        bottomPlayerCard   = findViewById(R.id.bottomPlayerCard)
        txtTopName         = findViewById(R.id.txtTopPlayerName)
        txtBottomName      = findViewById(R.id.txtBottomPlayerName)
        txtTopCaptured     = findViewById(R.id.txtTopCaptured)
        txtBottomCaptured  = findViewById(R.id.txtBottomCaptured)
        dotTopTurn         = findViewById(R.id.dotTopTurn)
        dotBottomTurn      = findViewById(R.id.dotBottomTurn)
        txtTopTurnLabel    = findViewById(R.id.txtTopTurnLabel)
        txtBottomTurnLabel = findViewById(R.id.txtBottomTurnLabel)
        txtStatus          = findViewById(R.id.txtStatus)
        txtDiffBadge       = findViewById(R.id.txtDifficultyBadge)
        txtModeLabel       = findViewById(R.id.txtModeLabel)
        imgTopPlayer       = findViewById(R.id.imgTopPlayer)
        imgBottomPlayer    = findViewById(R.id.imgBottomPlayer)
        moveHistoryScroll  = findViewById(R.id.moveHistoryScroll)
        txtMoveHistory     = findViewById(R.id.txtMoveHistory)
    }

    // =========================================================================
    // Top bar
    // =========================================================================

    private fun setupTopBar() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        when (mode) {
            GameMode.COMPUTER -> {
                txtModeLabel.text    = "vs Scottfish"
                txtDiffBadge.visibility = View.VISIBLE
                txtDiffBadge.text    = "Lvl $difficulty"
                txtBottomName.text   = "You"
                txtTopName.text      = "Scottfish AI"
                imgTopPlayer.text    = "🤖"
                imgBottomPlayer.text = if (playerIsWhite) "♔" else "♚"
            }
            GameMode.FRIEND -> {
                txtModeLabel.text    = "vs Friend"
                txtDiffBadge.visibility = View.GONE
                txtBottomName.text   = p1Name
                txtTopName.text      = p2Name
                imgBottomPlayer.text = "♔"
                imgTopPlayer.text    = "♚"
            }
            GameMode.ONLINE -> {
                txtModeLabel.text    = "Online"
                txtDiffBadge.visibility = View.GONE
            }
        }
    }

    // =========================================================================
    // Game start / load
    // =========================================================================

    private fun startNewGame() {
        isAbandoned    = false
        boardFlipped   = (mode == GameMode.COMPUTER && !playerIsWhite)
        lastMoveFrom   = null
        lastMoveTo     = null
        lastMovedPiece = null
        moveLog.clear()
        board.clear()
        gameState.resetTurn()
        selectedPos = null
        capturedByWhite.clear()
        capturedByBlack.clear()
        placeInitialPieces()
        txtMoveHistory.text = ""
        chessBoard.post {
            createBoardUI()
            if (mode == GameMode.COMPUTER && !playerIsWhite) triggerComputerMove()
        }
    }

    private fun createBoardUI() {
        chessBoard.removeAllViews()
        cellSize = minOf(chessBoard.width, chessBoard.height) / 8

        for (vr in 0 until 8) {
            for (vc in 0 until 8) {
                val boardPos = Position(bRow(vr), bCol(vc))
                val sqColor  = squareColor(vr, vc)
                val lblColor = if ((vr + vc) % 2 == 0) COLOR_DARK else COLOR_LIGHT

                val frame = FrameLayout(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = cellSize; height = cellSize
                    }
                    setBackgroundColor(sqColor)
                    tag = boardPos
                    setOnClickListener { if (!computerThinking) onCellClick(it as FrameLayout) }
                }

                // Child 0: Piece image
                frame.addView(ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(5, 5, 5, 5)
                })

                // Child 1: Move-dot
                val dotSz = cellSize / 3
                frame.addView(View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(dotSz, dotSz, Gravity.CENTER)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(DOT_COLOR)
                    }
                    visibility = View.GONE
                })

                // Child 2: Capture-ring
                val rm = dp(3)
                frame.addView(View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ).also { it.setMargins(rm, rm, rm, rm) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.TRANSPARENT)
                        setStroke(dp(5), RING_COLOR)
                    }
                    visibility = View.GONE
                })

                // Child 3: Rank label (left column)
                if (vc == 0) {
                    frame.addView(TextView(this).apply {
                        text     = (8 - bRow(vr)).toString()
                        textSize = 9f
                        setTextColor(lblColor)
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.START
                        ).also { it.setMargins(dp(2), dp(2), 0, 0) }
                    })
                }

                // Child 3/4: File label (bottom row)
                if (vr == 7) {
                    frame.addView(TextView(this).apply {
                        text     = ('a' + bCol(vc)).toString()
                        textSize = 9f
                        setTextColor(lblColor)
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM or Gravity.END
                        ).also { it.setMargins(0, 0, dp(2), dp(2)) }
                    })
                }

                chessBoard.addView(frame)
                cells[vr][vc] = frame
            }
        }
        refreshBoard()
        updateTurnUI()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    // =========================================================================
    // Cell click
    // =========================================================================

    private fun onCellClick(cell: FrameLayout) {
        if (mode == GameMode.COMPUTER && gameState.whiteTurn != playerIsWhite) return

        val pos    = cell.tag as Position
        val tapped = board.getPiece(pos)

        if (selectedPos == null) {
            if (tapped == null || tapped.isWhite != gameState.whiteTurn) return
            selectedPos = pos
            highlightMoves(pos, tapped)
            return
        }

        val selPiece = board.getPiece(selectedPos!!)
        if (tapped != null && selPiece != null && tapped.isWhite == selPiece.isWhite) {
            resetSelection()
            selectedPos = pos
            highlightMoves(pos, tapped)
            return
        }

        attemptMove(selectedPos!!, pos)
    }

    // =========================================================================
    // Move execution
    // =========================================================================

    private fun attemptMove(from: Position, to: Position) {
        val piece = board.getPiece(from) ?: run { resetSelection(); return }

        if (!generator.validator.isValidMove(from, to, piece) ||
            !generator.isMoveSafe(from, to, piece)) {
            resetSelection(); return
        }

        val isCastling  = piece.type == PieceType.KING && abs(to.col - from.col) == 2
        val isEnPassant = piece.type == PieceType.PAWN &&
                          abs(to.col - from.col) == 1 &&
                          board.getPiece(to) == null

        if (piece.type == PieceType.PAWN && (to.row == 0 || to.row == 7) && !isEnPassant) {
            if (mode == GameMode.COMPUTER && piece.isWhite != playerIsWhite) {
                executeMove(from, to, PieceType.QUEEN, isCastling, isEnPassant)
            } else {
                showPromotionDialog(piece.isWhite) { executeMove(from, to, it, isCastling, isEnPassant) }
            }
            return
        }
        executeMove(from, to, null, isCastling, isEnPassant)
    }

    private fun executeMove(
        from: Position,
        to: Position,
        promoteTo: PieceType?,
        isCastling: Boolean,
        isEnPassant: Boolean
    ) {
        val piece    = board.getPiece(from) ?: return
        val captured = board.getPiece(to)

        // ── Build notation BEFORE altering the board ──────────────────────────
        val notation = buildMoveNotation(from, to, piece, captured, promoteTo, isCastling, isEnPassant)

        // ── Handle en-passant capture ─────────────────────────────────────────
        if (isEnPassant) {
            val epPawnPos = Position(from.row, to.col)
            board.getPiece(epPawnPos)?.let { ep ->
                if (piece.isWhite) capturedByWhite.add(ep.type)
                else               capturedByBlack.add(ep.type)
                board.setPiece(epPawnPos, null)
            }
        } else if (captured != null) {
            if (piece.isWhite) capturedByWhite.add(captured.type)
            else               capturedByBlack.add(captured.type)
        }

        // ── Place piece at destination ────────────────────────────────────────
        val finalPiece = if (promoteTo != null)
            ChessPiece(promoteTo, piece.isWhite, getImage(promoteTo, piece.isWhite))
        else piece

        board.setPiece(to, finalPiece)
        board.setPiece(from, null)

        // ── Handle castling: slide the rook ───────────────────────────────────
        if (isCastling) {
            val rank       = from.row
            val isKingSide = to.col > from.col
            val rookFrom   = Position(rank, if (isKingSide) 7 else 0)
            val rookTo     = Position(rank, if (isKingSide) 5 else 3)
            board.setPiece(rookTo, board.getPiece(rookFrom))
            board.setPiece(rookFrom, null)
        }

        // ── Update castling rights ────────────────────────────────────────────
        updateCastlingRights(from, to, piece)

        // ── Update en-passant target ──────────────────────────────────────────
        if (piece.type == PieceType.PAWN && abs(to.row - from.row) == 2) {
            val epRow = (from.row + to.row) / 2
            gameState.enPassantTarget = Position(epRow, from.col)
        } else {
            gameState.enPassantTarget = null
        }

        // ── Sound feedback ────────────────────────────────────────────────────
        when {
            isCastling  -> sound.playCastle()
            captured != null || isEnPassant -> sound.playCapture()
            else        -> sound.playMove()
        }

        // ── Record & render ───────────────────────────────────────────────────
        moveLog.add(notation)
        lastMoveFrom   = from
        lastMoveTo     = to
        lastMovedPiece = finalPiece.type

        resetSelection()
        refreshBoard()
        updateMoveHistory()
        checkGameEnd { continueAfterMove() }
    }

    private fun continueAfterMove() {
        gameState.switchTurn()
        updateTurnUI()
        if (mode == GameMode.COMPUTER && gameState.whiteTurn != playerIsWhite) {
            triggerComputerMove()
        }
    }

    // ── Castling rights maintenance ───────────────────────────────────────────

    private fun updateCastlingRights(from: Position, to: Position, piece: ChessPiece) {
        // King moved → lose both castling rights
        if (piece.type == PieceType.KING) {
            gameState.revokeAllCastle(piece.isWhite)
        }
        // Rook moved from its starting square
        if (piece.type == PieceType.ROOK) {
            if (piece.isWhite) {
                if (from == Position(7, 7)) gameState.revokeCastleKS(true)
                if (from == Position(7, 0)) gameState.revokeCastleQS(true)
            } else {
                if (from == Position(0, 7)) gameState.revokeCastleKS(false)
                if (from == Position(0, 0)) gameState.revokeCastleQS(false)
            }
        }
        // Opponent rook captured on its starting square → revoke opponent's right
        if (to == Position(7, 7)) gameState.revokeCastleKS(true)
        if (to == Position(7, 0)) gameState.revokeCastleQS(true)
        if (to == Position(0, 7)) gameState.revokeCastleKS(false)
        if (to == Position(0, 0)) gameState.revokeCastleQS(false)
    }

    // =========================================================================
    // Computer move
    // =========================================================================

    private fun triggerComputerMove() {
        computerThinking = true
        updateTurnUI()

        lifecycleScope.launch {
            val move = withContext(Dispatchers.Default) {
                ai.findBestMove(isWhite = !playerIsWhite, difficulty = difficulty)
            }
            computerThinking = false

            if (move == null) { showEndDialog("Draw", "No moves available."); return@launch }

            val piece    = board.getPiece(move.from) ?: return@launch
            val captured = board.getPiece(move.to)
            val isCastling  = piece.type == PieceType.KING && abs(move.to.col - move.from.col) == 2
            val isEnPassant = piece.type == PieceType.PAWN &&
                              abs(move.to.col - move.from.col) == 1 &&
                              captured == null

            val notation = buildMoveNotation(
                move.from, move.to, piece, captured, move.promoteTo,
                isCastling, isEnPassant
            )

            // Capture tracking
            if (isEnPassant) {
                val epPawnPos = Position(move.from.row, move.to.col)
                board.getPiece(epPawnPos)?.let { ep ->
                    if (!playerIsWhite) capturedByWhite.add(ep.type)
                    else                capturedByBlack.add(ep.type)
                    board.setPiece(epPawnPos, null)
                }
            } else if (captured != null) {
                if (!playerIsWhite) capturedByWhite.add(captured.type)
                else                capturedByBlack.add(captured.type)
            }

            val finalPiece = if (move.promoteTo != null)
                ChessPiece(move.promoteTo, piece.isWhite, getImage(move.promoteTo, piece.isWhite))
            else piece

            board.setPiece(move.to, finalPiece)
            board.setPiece(move.from, null)

            // Castling: slide rook
            if (isCastling) {
                val rank       = move.from.row
                val isKingSide = move.to.col > move.from.col
                val rookFrom   = Position(rank, if (isKingSide) 7 else 0)
                val rookTo     = Position(rank, if (isKingSide) 5 else 3)
                board.setPiece(rookTo, board.getPiece(rookFrom))
                board.setPiece(rookFrom, null)
            }

            // Update castling rights and en-passant target
            updateCastlingRights(move.from, move.to, piece)
            if (piece.type == PieceType.PAWN && abs(move.to.row - move.from.row) == 2) {
                val epRow = (move.from.row + move.to.row) / 2
                gameState.enPassantTarget = Position(epRow, move.from.col)
            } else {
                gameState.enPassantTarget = null
            }

            // Sound feedback for computer move
            when {
                isCastling  -> sound.playCastle()
                captured != null || isEnPassant -> sound.playCapture()
                else        -> sound.playMove()
            }

            moveLog.add(notation)
            lastMoveFrom   = move.from
            lastMoveTo     = move.to
            lastMovedPiece = finalPiece.type

            applyLastMoveHighlight()
            refreshBoard()
            updateMoveHistory()

            checkGameEnd {
                gameState.switchTurn()
                updateTurnUI()
            }
        }
    }

    // =========================================================================
    // Game-end detection
    // =========================================================================

    private fun checkGameEnd(onContinue: () -> Unit) {
        val opponent = !gameState.whiteTurn
        when {
            generator.isCheckmate(opponent) -> {
                val winner = if (gameState.whiteTurn) "White" else "Black"
                sound.playWin()
                updateTurnUI()
                showEndDialog("Checkmate!", "$winner wins!")
            }
            generator.isStalemate(opponent) -> {
                sound.playDraw()
                showEndDialog("Stalemate", "It's a draw!")
            }
            generator.isKingInCheck(opponent) -> {
                sound.playCheck()
                onContinue()
            }
            else -> onContinue()
        }
    }

    // =========================================================================
    // Move notation helpers
    // =========================================================================

    /**
     * Builds standard algebraic notation for one half-move.
     * Call this BEFORE the board is mutated so capture detection is correct.
     */
    private fun buildMoveNotation(
        from: Position,
        to: Position,
        piece: ChessPiece,
        captured: ChessPiece?,
        promoteTo: PieceType?,
        isCastling: Boolean,
        isEnPassant: Boolean
    ): String {
        if (isCastling) return if (to.col == 6) "O-O" else "O-O-O"

        val pieceChar = when (piece.type) {
            PieceType.PAWN   -> ""
            PieceType.ROOK   -> "R"
            PieceType.KNIGHT -> "N"
            PieceType.BISHOP -> "B"
            PieceType.QUEEN  -> "Q"
            PieceType.KING   -> "K"
        }
        val isCapture = captured != null || isEnPassant
        val fromFile  = ('a' + from.col).toString()
        val toSq      = "${('a' + to.col)}${8 - to.row}"

        val base = if (piece.type == PieceType.PAWN) {
            if (isCapture) "${fromFile}x$toSq" else toSq
        } else {
            if (isCapture) "${pieceChar}x$toSq" else "$pieceChar$toSq"
        }

        return if (promoteTo != null) {
            val promoChar = when (promoteTo) {
                PieceType.QUEEN  -> "Q"
                PieceType.ROOK   -> "R"
                PieceType.BISHOP -> "B"
                PieceType.KNIGHT -> "N"
                else             -> "Q"
            }
            "$base=$promoChar"
        } else base
    }

    /** Renders the move log as "1. e4  e5   2. Nf3  Nc6 …" */
    private fun updateMoveHistory() {
        val sb = StringBuilder()
        var i = 0
        while (i < moveLog.size) {
            val moveNum  = i / 2 + 1
            val whiteMov = moveLog[i]
            val blackMov = if (i + 1 < moveLog.size) moveLog[i + 1] else ""
            sb.append("%d. %-8s".format(moveNum, whiteMov))
            if (blackMov.isNotEmpty()) sb.append("%-8s  ".format(blackMov))
            i += 2
        }
        txtMoveHistory.text = sb.toString().trimEnd()
        moveHistoryScroll.post { moveHistoryScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT) }
    }

    // =========================================================================
    // Turn UI
    // =========================================================================

    private fun updateTurnUI() {
        val whiteTurn = gameState.whiteTurn
        val inCheck   = generator.isKingInCheck(whiteTurn)
        val thinking  = computerThinking

        when {
            thinking -> {
                txtStatus.visibility = View.VISIBLE
                txtStatus.text = "⏳  Scottfish is thinking…"
                txtStatus.setTextColor(Color.parseColor("#D4AF37"))
            }
            inCheck -> {
                txtStatus.visibility = View.VISIBLE
                txtStatus.text = "♚  ${if (whiteTurn) "White" else "Black"} is in CHECK!"
                txtStatus.setTextColor(Color.parseColor("#E74C3C"))
            }
            lastMoveTo != null -> {
                txtStatus.visibility = View.VISIBLE
                val who = if (whiteTurn) "Black" else "White"
                txtStatus.text = "$who played ${lastStatusNotation()}"
                txtStatus.setTextColor(Color.parseColor("#99B0B8D0"))
            }
            else -> txtStatus.visibility = View.INVISIBLE
        }

        val bottomActive = (whiteTurn != boardFlipped) && !thinking
        val topActive    = (whiteTurn == boardFlipped) && !thinking

        bottomPlayerCard.setBackgroundResource(
            if (bottomActive) R.drawable.bg_player_card_active else R.drawable.bg_player_card)
        topPlayerCard.setBackgroundResource(
            if (topActive) R.drawable.bg_player_card_active else R.drawable.bg_player_card)

        dotBottomTurn.visibility      = if (bottomActive) View.VISIBLE  else View.INVISIBLE
        dotTopTurn.visibility         = if (topActive)    View.VISIBLE  else View.INVISIBLE
        txtBottomTurnLabel.visibility = if (bottomActive) View.VISIBLE  else View.INVISIBLE
        txtTopTurnLabel.visibility    = if (topActive)    View.VISIBLE  else View.INVISIBLE

        if (!boardFlipped) {
            txtBottomCaptured.text = capturedByWhite.joinToString("") { pieceSymbol(it, false) }
            txtTopCaptured.text    = capturedByBlack.joinToString("") { pieceSymbol(it, true)  }
        } else {
            txtBottomCaptured.text = capturedByBlack.joinToString("") { pieceSymbol(it, true)  }
            txtTopCaptured.text    = capturedByWhite.joinToString("") { pieceSymbol(it, false) }
        }

        refreshCheckHighlight()
    }

    /** Short notation used in the status strip (e.g. "Ne2→e4", "O-O"). */
    private fun lastStatusNotation(): String {
        val from  = lastMoveFrom  ?: return ""
        val to    = lastMoveTo    ?: return ""
        val piece = lastMovedPiece ?: return ""
        val sym = when (piece) {
            PieceType.PAWN   -> ""
            PieceType.ROOK   -> "R"
            PieceType.KNIGHT -> "N"
            PieceType.BISHOP -> "B"
            PieceType.QUEEN  -> "Q"
            PieceType.KING   -> "K"
        }
        val fromSq = "${('a' + from.col)}${8 - from.row}"
        val toSq   = "${('a' + to.col)}${8 - to.row}"
        return "$sym$fromSq→$toSq"
    }

    private fun pieceSymbol(t: PieceType, white: Boolean) = when (t) {
        PieceType.PAWN   -> if (white) "♙" else "♟"
        PieceType.ROOK   -> if (white) "♖" else "♜"
        PieceType.KNIGHT -> if (white) "♘" else "♞"
        PieceType.BISHOP -> if (white) "♗" else "♝"
        PieceType.QUEEN  -> if (white) "♕" else "♛"
        PieceType.KING   -> if (white) "♔" else "♚"
    }

    // =========================================================================
    // Board rendering
    // =========================================================================

    private fun refreshBoard() {
        // Piece-style colour filter: 0=Classic (none), 1=Warm sepia, 2=Ice blue
        val filter: android.graphics.ColorFilter? = when (settings.pieceStyle) {
            1 -> android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply {
                // Warm sepia: boost red/green, reduce blue
                set(floatArrayOf(
                    1.1f, 0.2f, 0.0f, 0f, 10f,
                    0.0f, 1.0f, 0.0f, 0f,  5f,
                    0.0f, 0.0f, 0.7f, 0f,  0f,
                    0.0f, 0.0f, 0.0f, 1f,  0f
                ))
            })
            2 -> android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply {
                // Ice blue: boost blue, cool down reds
                set(floatArrayOf(
                    0.8f, 0.0f, 0.1f, 0f,  0f,
                    0.0f, 0.9f, 0.1f, 0f,  5f,
                    0.0f, 0.0f, 1.2f, 0f, 15f,
                    0.0f, 0.0f, 0.0f, 1f,  0f
                ))
            })
            else -> null
        }

        for (vr in 0..7) for (vc in 0..7) {
            val frame = cells[vr][vc] ?: continue
            val piece = board.getPiece(Position(bRow(vr), bCol(vc)))
            val iv    = frame.getChildAt(0) as? ImageView ?: continue
            iv.setImageResource(piece?.imageRes ?: 0)
            iv.colorFilter = if (piece != null) filter else null
        }
    }

    private fun highlightMoves(from: Position, piece: ChessPiece) {
        resetHighlights()
        cells[dRow(from.row)][dCol(from.col)]?.setBackgroundColor(COLOR_SELECTED)

        for (r in 0..7) for (c in 0..7) {
            val to = Position(r, c)
            if (!generator.validator.isValidMove(from, to, piece) ||
                !generator.isMoveSafe(from, to, piece)) continue

            val frame = cells[dRow(r)][dCol(c)] ?: continue
            val dot   = frame.getChildAt(1) as? View
            val ring  = frame.getChildAt(2) as? View

            // For castling highlight: the rook's original square has a piece but we
            // want to show the KING's destination square (g1/c1) which is empty.
            // En-passant target is also an empty square → show dot.
            if (board.getPiece(to) != null && !(piece.type == PieceType.KING && abs(to.col - from.col) == 2))
                ring?.visibility = View.VISIBLE
            else
                dot?.visibility = View.VISIBLE
        }
    }

    private fun resetHighlights() {
        for (vr in 0..7) for (vc in 0..7) {
            val frame = cells[vr][vc] ?: continue
            frame.setBackgroundColor(squareColor(vr, vc))
            (frame.getChildAt(1) as? View)?.visibility = View.GONE
            (frame.getChildAt(2) as? View)?.visibility = View.GONE
        }
        applyLastMoveHighlight()
        refreshCheckHighlight()
    }

    private fun applyLastMoveHighlight() {
        fun tint(pos: Position) {
            val vr = dRow(pos.row); val vc = dCol(pos.col)
            cells[vr][vc]?.setBackgroundColor(
                if ((vr + vc) % 2 == 0) COLOR_LAST_LIGHT else COLOR_LAST_DARK
            )
        }
        lastMoveFrom?.let { tint(it) }
        lastMoveTo?.let   { tint(it) }
    }

    private fun refreshCheckHighlight() {
        for (white in listOf(true, false)) {
            if (!generator.isKingInCheck(white)) continue
            val kPos = board.allPieces().entries
                .firstOrNull { it.value.type == PieceType.KING && it.value.isWhite == white }
                ?.key ?: continue
            cells[dRow(kPos.row)][dCol(kPos.col)]?.setBackgroundColor(COLOR_CHECK)
        }
    }

    private fun resetSelection() {
        selectedPos = null
        resetHighlights()
    }

    private fun squareColor(vr: Int, vc: Int) =
        if ((vr + vc) % 2 == 0) COLOR_LIGHT else COLOR_DARK

    // =========================================================================
    // Piece placement
    // =========================================================================

    private fun placeInitialPieces() {
        val backRank = listOf(
            PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP,
            PieceType.QUEEN, PieceType.KING,
            PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
        )
        for (c in 0..7) {
            board.setPiece(Position(0, c), makePiece(backRank[c], false))
            board.setPiece(Position(1, c), makePiece(PieceType.PAWN, false))
            board.setPiece(Position(6, c), makePiece(PieceType.PAWN, true))
            board.setPiece(Position(7, c), makePiece(backRank[c], true))
        }
    }

    private fun makePiece(t: PieceType, white: Boolean) = ChessPiece(t, white, getImage(t, white))

    private fun getImage(t: PieceType, white: Boolean) = when (t) {
        PieceType.PAWN   -> if (white) R.drawable.white_pawn   else R.drawable.black_pawn
        PieceType.ROOK   -> if (white) R.drawable.white_rook   else R.drawable.black_rook
        PieceType.KNIGHT -> if (white) R.drawable.white_knight else R.drawable.black_knight
        PieceType.BISHOP -> if (white) R.drawable.white_bishop else R.drawable.black_bishop
        PieceType.QUEEN  -> if (white) R.drawable.white_queen  else R.drawable.black_queen
        PieceType.KING   -> if (white) R.drawable.white_king   else R.drawable.black_king
    }

    // =========================================================================
    // Save / Load
    // =========================================================================

    private fun saveGame() {
        if (mode == GameMode.ONLINE) return
        val data = board.allPieces().entries.joinToString(";") {
            "${it.key.row},${it.key.col},${it.value.type},${it.value.isWhite}"
        }
        storage.saveGame(mode, gameState.whiteTurn, data, difficulty, playerIsWhite)
    }

    private fun loadGame() {
        isAbandoned  = false
        boardFlipped = (mode == GameMode.COMPUTER && !playerIsWhite)
        board.clear()
        gameState.setTurn(storage.loadTurn(mode))
        capturedByWhite.clear()
        capturedByBlack.clear()
        moveLog.clear()
        lastMoveFrom   = null
        lastMoveTo     = null
        lastMovedPiece = null
        txtMoveHistory.text = ""

        storage.loadPieces(mode)
            ?.takeIf { it.isNotEmpty() }
            ?.split(";")
            ?.forEach { entry ->
                val p = entry.split(",")
                if (p.size == 4) {
                    val type    = PieceType.valueOf(p[2].uppercase())
                    val isWhite = p[3].toBoolean()
                    board.setPiece(Position(p[0].toInt(), p[1].toInt()), makePiece(type, isWhite))
                }
            }

        chessBoard.post { createBoardUI() }
    }

    // =========================================================================
    // Dialogs
    // =========================================================================

    private fun showPromotionDialog(isWhite: Boolean, onChoice: (PieceType) -> Unit) {
        val dialog = Dialog(this, R.style.Theme_MyChess)
        dialog.setContentView(R.layout.dialog_promotion)
        dialog.setCancelable(false)

        val types = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
        val ids   = listOf(R.id.btnPromoteQueen, R.id.btnPromoteRook,
                           R.id.btnPromoteBishop, R.id.btnPromoteKnight)
        types.zip(ids).forEach { (type, id) ->
            dialog.findViewById<ImageView>(id)?.let { v ->
                v.setImageResource(getImage(type, isWhite))
                v.setOnClickListener { dialog.dismiss(); onChoice(type) }
            }
        }
        dialog.show()
    }

    private fun showEndDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("New Game") { _, _ -> storage.clearGame(mode); startNewGame() }
            .setNegativeButton("Exit")     { _, _ -> finish() }
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Save & Exit")
            .setMessage("Save this game and return to the menu?")
            .setPositiveButton("Save & Exit") { _, _ ->
                isAbandoned = false
                saveGame()
                finish()
            }
            .setNegativeButton("Abandon") { _, _ ->
                // Mark abandoned so onPause does NOT re-save
                isAbandoned = true
                storage.clearGame(mode)
                finish()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
}
