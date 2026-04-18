package com.skd.mychess.ui

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
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
        const val EXTRA_MODE       = "game_mode"
        const val EXTRA_DIFFICULTY = "difficulty"
        const val EXTRA_RESUME     = "resume"

        // Board square colours
        private val COLOR_LIGHT    = Color.parseColor("#F0D9B5")
        private val COLOR_DARK     = Color.parseColor("#B58863")
        private val COLOR_SELECTED = Color.parseColor("#C8D4AF37") // gold ~78% alpha (ARGB)
        private val COLOR_MOVE     = Color.parseColor("#9944BB44") // green ~60%
        private val COLOR_CAPTURE  = Color.parseColor("#99CC4444") // red-green ~60%
        private val COLOR_CHECK    = Color.parseColor("#CCFF3A3A") // red ~80%
    }

    // Engine
    private val board       = ChessBoard()
    private lateinit var validator: MoveValidator
    private lateinit var generator: MoveGenerator
    private lateinit var ai:        ScottfishEngine
    private val gameState   = GameStateManager()

    // Mode
    private lateinit var mode: GameMode
    private var difficulty     = 5
    private val playerIsWhite  = true
    private var computerThinking = false

    // UI
    private lateinit var chessBoard:        GridLayout
    private val cells = Array(8) { Array<ImageView?>(8) { null } }
    private var selectedPos: Position? = null

    private lateinit var topPlayerCard:     LinearLayout
    private lateinit var bottomPlayerCard:  LinearLayout
    private lateinit var txtTopName:        TextView
    private lateinit var txtBottomName:     TextView
    private lateinit var txtTopCaptured:    TextView
    private lateinit var txtBottomCaptured: TextView
    private lateinit var dotTopTurn:        View
    private lateinit var dotBottomTurn:     View
    private lateinit var txtTopTurnLabel:   TextView
    private lateinit var txtBottomTurnLabel:TextView
    private lateinit var txtStatus:         TextView
    private lateinit var txtDiffBadge:      TextView
    private lateinit var txtModeLabel:      TextView
    private lateinit var imgTopPlayer:      TextView
    private lateinit var imgBottomPlayer:   TextView

    // Storage
    private lateinit var storage: LocalGameStorage

    // Captured pieces
    private val capturedByWhite = mutableListOf<PieceType>()
    private val capturedByBlack = mutableListOf<PieceType>()

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        mode       = GameMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: GameMode.FRIEND.name)
        difficulty = intent.getIntExtra(EXTRA_DIFFICULTY, 5)
        val resume = intent.getBooleanExtra(EXTRA_RESUME, false)

        storage   = LocalGameStorage(this)
        validator = MoveValidator(board)
        generator = MoveGenerator(board)
        ai        = ScottfishEngine(board)

        bindViews()
        setupTopBar()

        if (resume && storage.hasSavedGame(mode)) loadGame()
        else startNewGame()
    }

    override fun onPause() {
        super.onPause()
        if (!computerThinking) saveGame()
    }

    // ─── View binding ────────────────────────────────────────────────────────

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

    // ─── Top bar setup ───────────────────────────────────────────────────────

    private fun setupTopBar() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        when (mode) {
            GameMode.COMPUTER -> {
                txtModeLabel.text   = "vs Scottfish"
                txtTopName.text     = "Scottfish AI"
                txtBottomName.text  = "You"
                imgTopPlayer.text   = "🤖"
                imgBottomPlayer.text= "♔"
                txtDiffBadge.visibility = View.VISIBLE
                txtDiffBadge.text   = "Lvl $difficulty"
            }
            GameMode.FRIEND -> {
                txtModeLabel.text   = "vs Friend"
                txtTopName.text     = "Black"
                txtBottomName.text  = "White"
                imgTopPlayer.text   = "♚"
                imgBottomPlayer.text= "♔"
                txtDiffBadge.visibility = View.GONE
            }
            GameMode.ONLINE -> {
                txtModeLabel.text   = "Online"
                txtDiffBadge.visibility = View.GONE
            }
        }
    }

    // ─── Game start / load ───────────────────────────────────────────────────

    private fun startNewGame() {
        board.clear()
        gameState.resetTurn()
        selectedPos = null
        capturedByWhite.clear()
        capturedByBlack.clear()
        placeInitialPieces()
        chessBoard.post { createBoardUI() }
    }

    private fun createBoardUI() {
        chessBoard.removeAllViews()
        val size = chessBoard.width / 8

        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val cell = ImageView(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width  = size
                        height = size
                    }
                    setBackgroundColor(squareColor(r, c))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(4, 4, 4, 4)
                    tag = Position(r, c)
                    setOnClickListener { if (!computerThinking) onCellClick(it as ImageView) }
                }
                chessBoard.addView(cell)
                cells[r][c] = cell
            }
        }
        refreshBoard()
        updateTurnUI()
    }

    // ─── Cell click ──────────────────────────────────────────────────────────

    private fun onCellClick(cell: ImageView) {
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

    // ─── Move execution ──────────────────────────────────────────────────────

    private fun attemptMove(from: Position, to: Position) {
        val piece = board.getPiece(from) ?: run { resetSelection(); return }

        if (!validator.isValidMove(from, to, piece) || !generator.isMoveSafe(from, to, piece)) {
            resetSelection()
            return
        }

        if (piece.type == PieceType.PAWN && (to.row == 0 || to.row == 7)) {
            if (mode == GameMode.COMPUTER && !piece.isWhite) {
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
        resetSelection()
        refreshBoard()

        checkGameEnd { continueAfterMove() }
    }

    private fun continueAfterMove() {
        gameState.switchTurn()
        updateTurnUI()
        if (mode == GameMode.COMPUTER && gameState.whiteTurn != playerIsWhite) triggerComputerMove()
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
            if (captured != null) capturedByBlack.add(captured.type)

            val finalPiece = if (move.promoteTo != null)
                ChessPiece(move.promoteTo, piece.isWhite, getImage(move.promoteTo, piece.isWhite))
            else piece

            board.setPiece(move.to, finalPiece)
            board.setPiece(move.from, null)
            refreshBoard()

            checkGameEnd {
                gameState.switchTurn()
                updateTurnUI()
            }
        }
    }

    // ─── Game-end check ──────────────────────────────────────────────────────

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

    // ─── Turn UI ─────────────────────────────────────────────────────────────

    private fun updateTurnUI() {
        val whiteTurn = gameState.whiteTurn
        val inCheck   = generator.isKingInCheck(whiteTurn)
        val thinking  = computerThinking

        // Status strip
        txtStatus.visibility = if (inCheck || thinking) View.VISIBLE else View.GONE
        when {
            thinking -> {
                txtStatus.text = "Scottfish is thinking…"
                txtStatus.setTextColor(Color.parseColor("#D4AF37"))
            }
            inCheck  -> {
                txtStatus.text = "${if (whiteTurn) "White" else "Black"} is in CHECK!"
                txtStatus.setTextColor(Color.parseColor("#E74C3C"))
            }
        }

        // bottom = white, top = black
        val whiteActive = whiteTurn && !thinking
        val blackActive = !whiteTurn && !thinking

        bottomPlayerCard.setBackgroundResource(
            if (whiteActive) R.drawable.bg_player_card_active else R.drawable.bg_player_card)
        topPlayerCard.setBackgroundResource(
            if (blackActive) R.drawable.bg_player_card_active else R.drawable.bg_player_card)

        dotBottomTurn.visibility       = if (whiteActive) View.VISIBLE else View.INVISIBLE
        dotTopTurn.visibility          = if (blackActive) View.VISIBLE else View.INVISIBLE
        txtBottomTurnLabel.visibility  = if (whiteActive) View.VISIBLE else View.INVISIBLE
        txtTopTurnLabel.visibility     = if (blackActive) View.VISIBLE else View.INVISIBLE

        // Captured pieces display
        txtBottomCaptured.text = capturedByWhite.joinToString("") { pieceSymbol(it, true) }
        txtTopCaptured.text    = capturedByBlack.joinToString("") { pieceSymbol(it, false) }

        refreshCheckHighlight()
    }

    private fun pieceSymbol(t: PieceType, white: Boolean) = when (t) {
        PieceType.PAWN   -> if (white) "♙" else "♟"
        PieceType.ROOK   -> if (white) "♖" else "♜"
        PieceType.KNIGHT -> if (white) "♘" else "♞"
        PieceType.BISHOP -> if (white) "♗" else "♝"
        PieceType.QUEEN  -> if (white) "♕" else "♛"
        PieceType.KING   -> if (white) "♔" else "♚"
    }

    // ─── Board rendering ─────────────────────────────────────────────────────

    private fun refreshBoard() {
        for (r in 0..7) for (c in 0..7) {
            cells[r][c]?.setImageResource(board.getPiece(Position(r, c))?.imageRes ?: 0)
        }
    }

    private fun highlightMoves(from: Position, piece: ChessPiece) {
        resetHighlights()
        // Gold background on selected piece cell
        cells[from.row][from.col]?.setBackgroundColor(COLOR_SELECTED)

        for (r in 0..7) for (c in 0..7) {
            val to = Position(r, c)
            if (!validator.isValidMove(from, to, piece) || !generator.isMoveSafe(from, to, piece)) continue
            val isCapture = board.getPiece(to) != null
            cells[r][c]?.setBackgroundColor(if (isCapture) COLOR_CAPTURE else COLOR_MOVE)
        }
    }

    private fun resetHighlights() {
        for (r in 0..7) for (c in 0..7)
            cells[r][c]?.setBackgroundColor(squareColor(r, c))
        refreshCheckHighlight()
    }

    private fun refreshCheckHighlight() {
        for (white in listOf(true, false)) {
            if (!generator.isKingInCheck(white)) continue
            val kPos = board.allPieces().entries
                .firstOrNull { it.value.type == PieceType.KING && it.value.isWhite == white }
                ?.key ?: continue
            cells[kPos.row][kPos.col]?.setBackgroundColor(COLOR_CHECK)
        }
    }

    private fun resetSelection() {
        selectedPos = null
        resetHighlights()
    }

    private fun squareColor(r: Int, c: Int) =
        if ((r + c) % 2 == 0) COLOR_LIGHT else COLOR_DARK

    // ─── Pieces ──────────────────────────────────────────────────────────────

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

    // ─── Save / Load ─────────────────────────────────────────────────────────

    private fun saveGame() {
        if (mode == GameMode.ONLINE) return
        val data = board.allPieces().entries.joinToString(";") {
            "${it.key.row},${it.key.col},${it.value.type},${it.value.isWhite}"
        }
        storage.saveGame(mode, gameState.whiteTurn, data, difficulty)
    }

    private fun loadGame() {
        board.clear()
        gameState.setTurn(storage.loadTurn(mode))
        capturedByWhite.clear()
        capturedByBlack.clear()

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

    // ─── Dialogs ─────────────────────────────────────────────────────────────

    private fun showPromotionDialog(isWhite: Boolean, onChoice: (PieceType) -> Unit) {
        val dialog = Dialog(this, R.style.Theme_MyChess)
        dialog.setContentView(R.layout.dialog_promotion)
        dialog.setCancelable(false)

        val types = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
        val ids   = listOf(R.id.btnPromoteQueen, R.id.btnPromoteRook,
                           R.id.btnPromoteBishop, R.id.btnPromoteKnight)

        types.zip(ids).forEach { (type, id) ->
            dialog.findViewById<ImageView>(id)?.let { view ->
                view.setImageResource(getImage(type, isWhite))
                view.setOnClickListener { dialog.dismiss(); onChoice(type) }
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
