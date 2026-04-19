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
import com.skd.mychess.storage.LocalGameStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE         = "game_mode"
        const val EXTRA_DIFFICULTY   = "difficulty"
        const val EXTRA_RESUME       = "resume"
        const val EXTRA_PLAYER_WHITE = "player_is_white"
        const val EXTRA_P1_NAME      = "p1_name"
        const val EXTRA_P2_NAME      = "p2_name"

        // Board square base colours
        private val COLOR_LIGHT     = Color.parseColor("#F0D9B5")
        private val COLOR_DARK      = Color.parseColor("#B58863")
        // Selected square: warm gold tint
        private val COLOR_SELECTED  = Color.parseColor("#BBD4AF37")
        // King-in-check square
        private val COLOR_CHECK     = Color.parseColor("#CCFF3A3A")
        // Move-dot: semi-transparent dark circle on empty squares
        private val DOT_COLOR       = Color.parseColor("#55000000")
        // Capture-ring: darker oval stroke around occupied target
        private val RING_COLOR      = Color.parseColor("#CC000000")
        // Last-move highlight: chess.com yellow-green tint
        private val COLOR_LAST_LIGHT = Color.parseColor("#CCF6F644")  // on light squares
        private val COLOR_LAST_DARK  = Color.parseColor("#CCBACA2B")  // on dark squares
    }

    // ─── Engine ──────────────────────────────────────────────────────────────
    private val board     = ChessBoard()
    private lateinit var validator: MoveValidator
    private lateinit var generator: MoveGenerator
    private lateinit var ai:        ScottfishEngine
    private val gameState = GameStateManager()

    // ─── Game state ──────────────────────────────────────────────────────────
    private lateinit var mode: GameMode
    private var difficulty       = 5
    private var playerIsWhite    = true
    /** True when the board is rendered flipped (player's pieces at visual bottom). */
    private var boardFlipped     = false
    private var computerThinking = false
    private var p1Name           = "Player 1"
    private var p2Name           = "Player 2"

    // Last move tracking (board coordinates)
    private var lastMoveFrom:   Position?  = null
    private var lastMoveTo:     Position?  = null
    private var lastMovedPiece: PieceType? = null

    // ─── UI ──────────────────────────────────────────────────────────────────
    private lateinit var chessBoard: GridLayout
    private var cellSize = 0

    /**
     * cells[visualRow][visualCol] → FrameLayout.
     *   child 0 = piece ImageView
     *   child 1 = move-dot View    (small circle, empty targets)
     *   child 2 = capture-ring View (oval stroke, occupied targets)
     *   child 3 = rank label TextView (leftmost column only, optional)
     *   child 3/4 = file label TextView (bottom row only, optional)
     */
    private val cells = Array(8) { Array<FrameLayout?>(8) { null } }
    private var selectedPos: Position? = null   // board-coordinate position

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

    // ─── Storage ─────────────────────────────────────────────────────────────
    private lateinit var storage: LocalGameStorage

    // ─── Captured piece lists ─────────────────────────────────────────────────
    private val capturedByWhite = mutableListOf<PieceType>()
    private val capturedByBlack = mutableListOf<PieceType>()

    // =========================================================================
    // Coordinate helpers
    // =========================================================================

    /** Board row/col → visual row/col. */
    private fun dRow(br: Int) = if (boardFlipped) 7 - br else br
    private fun dCol(bc: Int) = if (boardFlipped) 7 - bc else bc

    /** Visual row/col → board row/col. */
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
        validator = MoveValidator(board)
        generator = MoveGenerator(board)
        ai        = ScottfishEngine(board)

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
        if (!computerThinking) saveGame()
    }

    // =========================================================================
    // View binding
    // =========================================================================

    private fun bindViews() {
        chessBoard        = findViewById(R.id.chessBoard)
        topPlayerCard     = findViewById(R.id.topPlayerCard)
        bottomPlayerCard  = findViewById(R.id.bottomPlayerCard)
        txtTopName        = findViewById(R.id.txtTopPlayerName)
        txtBottomName     = findViewById(R.id.txtBottomPlayerName)
        txtTopCaptured    = findViewById(R.id.txtTopCaptured)
        txtBottomCaptured = findViewById(R.id.txtBottomCaptured)
        dotTopTurn        = findViewById(R.id.dotTopTurn)
        dotBottomTurn     = findViewById(R.id.dotBottomTurn)
        txtTopTurnLabel   = findViewById(R.id.txtTopTurnLabel)
        txtBottomTurnLabel= findViewById(R.id.txtBottomTurnLabel)
        txtStatus         = findViewById(R.id.txtStatus)
        txtDiffBadge      = findViewById(R.id.txtDifficultyBadge)
        txtModeLabel      = findViewById(R.id.txtModeLabel)
        imgTopPlayer      = findViewById(R.id.imgTopPlayer)
        imgBottomPlayer   = findViewById(R.id.imgBottomPlayer)
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
                txtModeLabel.text = "vs Scottfish"
                txtDiffBadge.visibility = View.VISIBLE
                txtDiffBadge.text = "Lvl $difficulty"
                txtBottomName.text   = "You"
                txtTopName.text      = "Scottfish AI"
                imgTopPlayer.text    = "🤖"
                imgBottomPlayer.text = if (playerIsWhite) "♔" else "♚"
            }
            GameMode.FRIEND -> {
                txtModeLabel.text = "vs Friend"
                txtDiffBadge.visibility = View.GONE
                txtBottomName.text   = p1Name
                txtTopName.text      = p2Name
                imgBottomPlayer.text = "♔"
                imgTopPlayer.text    = "♚"
            }
            GameMode.ONLINE -> {
                txtModeLabel.text = "Online"
                txtDiffBadge.visibility = View.GONE
            }
        }
    }

    // =========================================================================
    // Game start / load
    // =========================================================================

    private fun startNewGame() {
        boardFlipped  = (mode == GameMode.COMPUTER && !playerIsWhite)
        lastMoveFrom  = null
        lastMoveTo    = null
        lastMovedPiece = null
        board.clear()
        gameState.resetTurn()
        selectedPos = null
        capturedByWhite.clear()
        capturedByBlack.clear()
        placeInitialPieces()
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
                val boardPos  = Position(bRow(vr), bCol(vc))
                val sqColor   = squareColor(vr, vc)
                // Label colour contrasts with the square
                val lblColor  = if ((vr + vc) % 2 == 0) COLOR_DARK else COLOR_LIGHT

                // ── Frame ──────────────────────────────────────────────────
                val frame = FrameLayout(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = cellSize; height = cellSize
                    }
                    setBackgroundColor(sqColor)
                    tag = boardPos
                    setOnClickListener { if (!computerThinking) onCellClick(it as FrameLayout) }
                }

                // ── Child 0: Piece image ────────────────────────────────────
                frame.addView(ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(5, 5, 5, 5)
                })

                // ── Child 1: Move-dot (small filled circle) ─────────────────
                val dotSz = cellSize / 3
                frame.addView(View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(dotSz, dotSz, Gravity.CENTER)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(DOT_COLOR)
                    }
                    visibility = View.GONE
                })

                // ── Child 2: Capture-ring (oval with stroke, no fill) ───────
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

                // ── Child 3: Rank label (left column only) ──────────────────
                // Rank = 8 − boardRow  (board row 0 = rank 8, board row 7 = rank 1)
                if (vc == 0) {
                    frame.addView(TextView(this).apply {
                        text      = (8 - bRow(vr)).toString()
                        textSize  = 9f
                        setTextColor(lblColor)
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.START
                        ).also { it.setMargins(dp(2), dp(2), 0, 0) }
                    })
                }

                // ── Child 3/4: File label (bottom row only) ─────────────────
                // File = 'a' + boardCol
                if (vr == 7) {
                    frame.addView(TextView(this).apply {
                        text      = ('a' + bCol(vc)).toString()
                        textSize  = 9f
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

        if (!validator.isValidMove(from, to, piece) || !generator.isMoveSafe(from, to, piece)) {
            resetSelection(); return
        }

        if (piece.type == PieceType.PAWN && (to.row == 0 || to.row == 7)) {
            if (mode == GameMode.COMPUTER && piece.isWhite != playerIsWhite) {
                executeMove(from, to, PieceType.QUEEN)
            } else {
                showPromotionDialog(piece.isWhite) { executeMove(from, to, it) }
            }
            return
        }
        executeMove(from, to, null)
    }

    private fun executeMove(from: Position, to: Position, promoteTo: PieceType?) {
        val piece    = board.getPiece(from) ?: return
        val captured = board.getPiece(to)

        if (captured != null) {
            if (piece.isWhite) capturedByWhite.add(captured.type)
            else               capturedByBlack.add(captured.type)
        }

        val finalPiece = if (promoteTo != null)
            ChessPiece(promoteTo, piece.isWhite, getImage(promoteTo, piece.isWhite))
        else piece

        board.setPiece(to, finalPiece)
        board.setPiece(from, null)

        // Record last move BEFORE resetSelection (which calls resetHighlights)
        lastMoveFrom   = from
        lastMoveTo     = to
        lastMovedPiece = finalPiece.type

        resetSelection()   // clears dots/rings, applies last-move highlight
        refreshBoard()
        checkGameEnd { continueAfterMove() }
    }

    private fun continueAfterMove() {
        gameState.switchTurn()
        updateTurnUI()
        if (mode == GameMode.COMPUTER && gameState.whiteTurn != playerIsWhite) {
            triggerComputerMove()
        }
    }

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
            if (captured != null) {
                if (!playerIsWhite) capturedByWhite.add(captured.type)
                else                capturedByBlack.add(captured.type)
            }

            val finalPiece = if (move.promoteTo != null)
                ChessPiece(move.promoteTo, piece.isWhite, getImage(move.promoteTo, piece.isWhite))
            else piece

            board.setPiece(move.to, finalPiece)
            board.setPiece(move.from, null)

            lastMoveFrom   = move.from
            lastMoveTo     = move.to
            lastMovedPiece = finalPiece.type

            // Apply last-move highlight, then refresh images
            applyLastMoveHighlight()
            refreshBoard()

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
                updateTurnUI()
                showEndDialog("Checkmate!", "$winner wins!")
            }
            generator.isStalemate(opponent) -> showEndDialog("Stalemate", "It's a draw!")
            else -> onContinue()
        }
    }

    // =========================================================================
    // Turn UI
    // =========================================================================

    private fun updateTurnUI() {
        val whiteTurn = gameState.whiteTurn
        val inCheck   = generator.isKingInCheck(whiteTurn)
        val thinking  = computerThinking

        // ── Status strip (fixed height → board never jumps) ──────────────────
        // Priority: thinking > check > last-move > invisible
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
                val who = if (whiteTurn) "Black" else "White"   // previous player
                txtStatus.text = "$who played ${moveNotation()}"
                txtStatus.setTextColor(Color.parseColor("#99B0B8D0"))
            }
            else -> txtStatus.visibility = View.INVISIBLE
        }

        // ── Active card highlighting ──────────────────────────────────────────
        // boardFlipped=false → bottom=White → active on whiteTurn
        // boardFlipped=true  → bottom=Black → active on !whiteTurn
        // General: bottomActive = whiteTurn XOR boardFlipped
        val bottomActive = (whiteTurn != boardFlipped) && !thinking
        val topActive    = (whiteTurn == boardFlipped) && !thinking

        bottomPlayerCard.setBackgroundResource(
            if (bottomActive) R.drawable.bg_player_card_active else R.drawable.bg_player_card)
        topPlayerCard.setBackgroundResource(
            if (topActive)    R.drawable.bg_player_card_active else R.drawable.bg_player_card)

        dotBottomTurn.visibility      = if (bottomActive) View.VISIBLE  else View.INVISIBLE
        dotTopTurn.visibility         = if (topActive)    View.VISIBLE  else View.INVISIBLE
        txtBottomTurnLabel.visibility = if (bottomActive) View.VISIBLE  else View.INVISIBLE
        txtTopTurnLabel.visibility    = if (topActive)    View.VISIBLE  else View.INVISIBLE

        // ── Captured pieces ───────────────────────────────────────────────────
        // capturedByWhite = black pieces white took → show black symbols
        // capturedByBlack = white pieces black took → show white symbols
        if (!boardFlipped) {
            txtBottomCaptured.text = capturedByWhite.joinToString("") { pieceSymbol(it, false) }
            txtTopCaptured.text    = capturedByBlack.joinToString("") { pieceSymbol(it, true)  }
        } else {
            txtBottomCaptured.text = capturedByBlack.joinToString("") { pieceSymbol(it, true)  }
            txtTopCaptured.text    = capturedByWhite.joinToString("") { pieceSymbol(it, false) }
        }

        refreshCheckHighlight()
    }

    /** Simple algebraic notation for the last move, e.g. "Ne2→e4" */
    private fun moveNotation(): String {
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
        for (vr in 0..7) for (vc in 0..7) {
            val frame = cells[vr][vc] ?: continue
            val piece = board.getPiece(Position(bRow(vr), bCol(vc)))
            (frame.getChildAt(0) as? ImageView)?.setImageResource(piece?.imageRes ?: 0)
        }
    }

    private fun highlightMoves(from: Position, piece: ChessPiece) {
        resetHighlights()   // resets squares + applies last-move colour underneath

        // Gold tint on selected piece's square
        cells[dRow(from.row)][dCol(from.col)]?.setBackgroundColor(COLOR_SELECTED)

        // Dot / ring on valid targets
        for (r in 0..7) for (c in 0..7) {
            val to = Position(r, c)
            if (!validator.isValidMove(from, to, piece) || !generator.isMoveSafe(from, to, piece)) continue

            val frame = cells[dRow(r)][dCol(c)] ?: continue
            val dot  = frame.getChildAt(1) as? View
            val ring = frame.getChildAt(2) as? View

            if (board.getPiece(to) != null) ring?.visibility = View.VISIBLE
            else                            dot?.visibility  = View.VISIBLE
        }
    }

    private fun resetHighlights() {
        for (vr in 0..7) for (vc in 0..7) {
            val frame = cells[vr][vc] ?: continue
            frame.setBackgroundColor(squareColor(vr, vc))
            (frame.getChildAt(1) as? View)?.visibility = View.GONE
            (frame.getChildAt(2) as? View)?.visibility = View.GONE
        }
        applyLastMoveHighlight()  // re-draw last-move squares beneath any new highlight
        refreshCheckHighlight()   // check highlight wins over everything
    }

    /** Tint the two squares of the last move with chess.com-style yellow-green. */
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
        boardFlipped = (mode == GameMode.COMPUTER && !playerIsWhite)
        board.clear()
        gameState.setTurn(storage.loadTurn(mode))
        capturedByWhite.clear()
        capturedByBlack.clear()
        lastMoveFrom  = null
        lastMoveTo    = null
        lastMovedPiece = null

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
            .setPositiveButton("Save & Exit")  { _, _ -> saveGame(); finish() }
            .setNegativeButton("Abandon")      { _, _ -> storage.clearGame(mode); finish() }
            .setNeutralButton("Cancel", null)
            .show()
    }
}
