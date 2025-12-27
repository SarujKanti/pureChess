package com.skd.mychess.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.skd.mychess.R
import com.skd.mychess.engine.ChessBoard
import com.skd.mychess.engine.GameStateManager
import com.skd.mychess.engine.MoveValidator
import com.skd.mychess.model.ChessPiece
import com.skd.mychess.model.PieceType
import com.skd.mychess.model.Position
import com.skd.mychess.storage.GameStorage

class MainActivity : AppCompatActivity() {

    private lateinit var chessBoardView: GridLayout
    private lateinit var homeLayout: LinearLayout
    private lateinit var playerInfoBar: LinearLayout
    private lateinit var btnExit2: Button
    private lateinit var txtTurn: TextView
    private lateinit var txtWhite: TextView
    private lateinit var txtBlack: TextView

    private val boardSize = 8
    private val cells = Array(boardSize) { Array<ImageView?>(boardSize) { null } }

    // Engine
    private val chessBoard = ChessBoard()
    private lateinit var moveValidator: MoveValidator
    private val gameState = GameStateManager()

    private var selectedPosition: Position? = null
    private lateinit var storage: GameStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chessBoardView = findViewById(R.id.chessBoard)
        playerInfoBar = findViewById(R.id.playerInfoBar)
        homeLayout = findViewById(R.id.home)
        btnExit2 = findViewById(R.id.btnExit2)
        txtTurn = findViewById(R.id.txtTurn)
        txtWhite = findViewById(R.id.txtWhite)
        txtBlack = findViewById(R.id.txtBlack)
        storage = GameStorage(getSharedPreferences("ChessGame", Context.MODE_PRIVATE))
        moveValidator = MoveValidator(chessBoard)

        setupButtons()
        createBoardUI()
    }

    private fun updateTurnUI() {
        val isWhiteTurn = gameState.whiteTurn

        // Highlight active player
        if (isWhiteTurn) {
            txtWhite.setTextColor(resources.getColor(R.color.move_highlight, theme))
            txtBlack.setTextColor(resources.getColor(android.R.color.white, theme))
        } else {
            txtBlack.setTextColor(resources.getColor(R.color.move_highlight, theme))
            txtWhite.setTextColor(resources.getColor(android.R.color.white, theme))
        }

        // Show CHECK status if applicable
        if (isKingInCheck(isWhiteTurn)) {
            txtTurn.text = "${if (isWhiteTurn) "White" else "Black"} is in CHECK!"
            txtTurn.setTextColor(android.graphics.Color.RED)
        } else {
            txtTurn.text = "${if (isWhiteTurn) "White" else "Black"}'s Turn"
            txtTurn.setTextColor(resources.getColor(R.color.color_yellow_dark, theme))
        }
    }


    // ---------------- BUTTONS ----------------

    private fun setupButtons() {

        findViewById<Button>(R.id.btnNewGame).setOnClickListener {
            chessBoardView.visibility = View.VISIBLE
            playerInfoBar.visibility = View.VISIBLE
            homeLayout.visibility = View.GONE
            btnExit2.visibility = View.VISIBLE
            startNewGame()
        }

        findViewById<Button>(R.id.btnContinueGame).setOnClickListener {
            chessBoardView.visibility = View.VISIBLE
            playerInfoBar.visibility = View.VISIBLE
            homeLayout.visibility = View.GONE
            btnExit2.visibility = View.VISIBLE
            loadGame()
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener {
            finishAffinity()
        }

        btnExit2.setOnClickListener {
            showExitDialog()
        }
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Yes") { _, _ ->
                saveGame()
                chessBoardView.visibility = View.GONE
                playerInfoBar.visibility = View.GONE
                homeLayout.visibility = View.VISIBLE
                btnExit2.visibility = View.GONE
            }
            .setNegativeButton("No", null)
            .show()
    }

    // ---------------- BOARD UI ----------------

    private fun createBoardUI() {
        chessBoardView.post {
            chessBoardView.removeAllViews()
            val size = chessBoardView.width / boardSize

            for (r in 0 until boardSize) {
                for (c in 0 until boardSize) {
                    val cell = ImageView(this)
                    cell.layoutParams = GridLayout.LayoutParams().apply {
                        width = size
                        height = size
                    }
                    cell.setBackgroundColor(getCellColor(r, c))
                    cell.tag = Position(r, c)
                    cell.setOnClickListener { onCellClick(it as ImageView) }

                    chessBoardView.addView(cell)
                    cells[r][c] = cell
                }
            }
        }
    }

    private fun getCellColor(r: Int, c: Int): Int =
        if ((r + c) % 2 == 0)
            resources.getColor(R.color.gray, theme)
        else
            resources.getColor(R.color.wooden_light, theme)

    // ---------------- GAME FLOW ----------------


    
    private fun startNewGame() {
        chessBoard.clear()
        gameState.resetTurn()   // ✅ SAME AS old currentTurn = true
        selectedPosition = null
        placeInitialPieces()
        refreshBoard()
        updateTurnUI()
    }

    private fun onCellClick(cell: ImageView) {
        val pos = cell.tag as Position
        val tappedPiece = chessBoard.getPiece(pos)

        // CASE 1: No piece selected yet
        if (selectedPosition == null) {
            if (tappedPiece == null) return
            if (tappedPiece.isWhite != gameState.whiteTurn) return

            selectedPosition = pos
            highlightMoves(pos, tappedPiece)
            return
        }

        val selectedPiece = chessBoard.getPiece(selectedPosition!!)

        // CASE 2: Tap another piece of SAME color → switch selection
        if (tappedPiece != null &&
            selectedPiece != null &&
            tappedPiece.isWhite == selectedPiece.isWhite
        ) {
            clearHighlight()
            selectedPosition = pos
            highlightMoves(pos, tappedPiece)
            return
        }

        // CASE 3: Try to move to empty / opponent square
        movePiece(selectedPosition!!, pos)
    }

    private fun movePiece(from: Position, to: Position) {
        val piece = chessBoard.getPiece(from) ?: return

        if (
            !moveValidator.isValidMove(from, to, piece) ||
            !isMoveSafe(from, to, piece)
        ) {
            refreshBoardState()
            selectedPosition = null
            return
        }

        chessBoard.setPiece(to, piece)
        chessBoard.setPiece(from, null)

        selectedPosition = null
        refreshBoard()
        refreshBoardState()

        when {
            !isKingAlive(true) -> {
                showWinner("Black")
                return
            }
            !isKingAlive(false) -> {
                showWinner("White")
                return
            }
        }

        gameState.switchTurn()
        updateTurnUI()
    }


    private fun getKingPosition(isWhite: Boolean): Position? {
        return chessBoard.allPieces()
            .entries
            .firstOrNull { it.value.type == PieceType.KING && it.value.isWhite == isWhite }
            ?.key
    }

    private fun isKingInCheck(isWhite: Boolean): Boolean {
        val kingPos = getKingPosition(isWhite) ?: return false

        for ((pos, piece) in chessBoard.allPieces()) {
            if (piece.isWhite != isWhite) {
                if (moveValidator.isValidMove(pos, kingPos, piece)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isMoveSafe(from: Position, to: Position, piece: ChessPiece): Boolean {
        // Save current state
        val capturedPiece = chessBoard.getPiece(to)

        // Make move temporarily
        chessBoard.setPiece(to, piece)
        chessBoard.setPiece(from, null)

        val kingInCheck = isKingInCheck(piece.isWhite)

        // Undo move
        chessBoard.setPiece(from, piece)
        if (capturedPiece != null) {
            chessBoard.setPiece(to, capturedPiece)
        } else {
            chessBoard.setPiece(to, null)
        }

        return !kingInCheck
    }


    private fun highlightCheckedKing() {
        val whiteKingPos = getKingPosition(true)
        val blackKingPos = getKingPosition(false)

        if (whiteKingPos != null && isKingInCheck(true)) {
            cells[whiteKingPos.row][whiteKingPos.col]
                ?.setBackgroundColor(android.graphics.Color.RED)
        }

        if (blackKingPos != null && isKingInCheck(false)) {
            cells[blackKingPos.row][blackKingPos.col]
                ?.setBackgroundColor(android.graphics.Color.RED)
        }
    }


    private fun refreshBoardState() {
        // 1️⃣ Reset board colors
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                val colorRes =
                    if ((r + c) % 2 == 0) R.color.gray else R.color.wooden_light

                cells[r][c]?.setBackgroundColor(
                    resources.getColor(colorRes, theme)
                )
            }
        }

        // 2️⃣ Re-apply CHECK highlight if still in check
        highlightCheckedKing()
    }



    private fun isKingAlive(isWhite: Boolean): Boolean {
        return chessBoard.allPieces().values.any {
            it.type == PieceType.KING && it.isWhite == isWhite
        }
    }


    private fun showWinner(winner: String) {
        AlertDialog.Builder(this)
            .setTitle("Game Over")
            .setMessage("$winner wins! King captured.")
            .setCancelable(false)
            .setPositiveButton("New Game") { _, _ ->
                startNewGame()
            }
            .setNegativeButton("Exit") { _, _ ->
                chessBoardView.visibility = View.GONE
                homeLayout.visibility = View.VISIBLE
                btnExit2.visibility = View.GONE
            }
            .show()
    }


    // ---------------- HIGHLIGHT ----------------

    private fun highlightMoves(from: Position, piece: ChessPiece) {
        refreshBoardState()

        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                val to = Position(r, c)

                if (
                    moveValidator.isValidMove(from, to, piece) &&
                    isMoveSafe(from, to, piece)
                ) {
                    cells[r][c]?.setBackgroundResource(
                        R.drawable.bg_move_highlight
                    )
                }
            }
        }
    }




    private fun clearHighlight() {
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                val colorRes =
                    if ((r + c) % 2 == 0) R.color.gray else R.color.wooden_light

                cells[r][c]?.setBackgroundColor(
                    resources.getColor(colorRes, theme)
                )
            }
        }
    }


    // ---------------- PIECES ----------------

    private fun placeInitialPieces() {
        for (c in 0..7) {
            chessBoard.setPiece(Position(1, c), black(PieceType.PAWN))
            chessBoard.setPiece(Position(6, c), white(PieceType.PAWN))
        }

        val back = listOf(
            PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP,
            PieceType.QUEEN, PieceType.KING,
            PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
        )

        back.forEachIndexed { c, t ->
            chessBoard.setPiece(Position(0, c), black(t))
            chessBoard.setPiece(Position(7, c), white(t))
        }
    }

    private fun refreshBoard() {
        for (r in 0 until boardSize)
            for (c in 0 until boardSize)
                cells[r][c]?.setImageResource(
                    chessBoard.getPiece(Position(r, c))?.imageRes ?: 0
                )
    }

    // ---------------- SAVE / LOAD ----------------

    private fun saveGame() {
        val data = chessBoard.allPieces().entries.joinToString(";") {
            "${it.key.row},${it.key.col},${it.value.type},${it.value.isWhite}"
        }
        storage.save(gameState.whiteTurn, data)
    }

    private fun loadGame() {
        chessBoard.clear()
        gameState.setTurn(storage.loadTurn())

        storage.loadPieces()
            ?.takeIf { it.isNotEmpty() }
            ?.split(";")
            ?.forEach { data ->
                val p = data.split(",")
                if (p.size == 4) {
                    val row = p[0].toInt()
                    val col = p[1].toInt()
                    val type = PieceType.valueOf(p[2].uppercase()) // ✅ FIX
                    val isWhite = p[3].toBoolean()

                    chessBoard.setPiece(
                        Position(row, col),
                        ChessPiece(type, isWhite, getImage(type, isWhite))
                    )
                }
            }

        refreshBoard()
        updateTurnUI()
    }


    // ---------------- HELPERS ----------------

    private fun white(t: PieceType) = ChessPiece(t, true, getImage(t, true))
    private fun black(t: PieceType) = ChessPiece(t, false, getImage(t, false))

    private fun getImage(t: PieceType, w: Boolean) = when (t) {
        PieceType.PAWN -> if (w) R.drawable.white_pawn else R.drawable.black_pawn
        PieceType.ROOK -> if (w) R.drawable.white_rook else R.drawable.black_rook
        PieceType.KNIGHT -> if (w) R.drawable.white_knight else R.drawable.black_knight
        PieceType.BISHOP -> if (w) R.drawable.white_bishop else R.drawable.black_bishop
        PieceType.QUEEN -> if (w) R.drawable.white_queen else R.drawable.black_queen
        PieceType.KING -> if (w) R.drawable.white_king else R.drawable.black_king
    }
}
