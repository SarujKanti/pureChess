package com.skd.mychess.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.skd.mychess.R
import com.skd.mychess.model.GameMode
import com.skd.mychess.storage.LocalGameStorage

class HomeActivity : AppCompatActivity() {

    private lateinit var storage: LocalGameStorage
    private var selectedDifficulty = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        storage = LocalGameStorage(this)

        setupDifficultySeekBar()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        updateResumeButtons()
    }

    // =========================================================================
    // Difficulty seek bar
    // =========================================================================

    private fun setupDifficultySeekBar() {
        val seekBar = findViewById<SeekBar>(R.id.seekDifficulty)
        val label   = findViewById<TextView>(R.id.txtDifficultyLabel)

        selectedDifficulty = storage.loadDifficulty().coerceIn(1, 10)
        seekBar.progress   = selectedDifficulty - 1
        label.text         = difficultyLabel(selectedDifficulty)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                selectedDifficulty = progress + 1
                label.text = difficultyLabel(selectedDifficulty)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun difficultyLabel(d: Int) = "$d — ${when {
        d <= 2  -> "Beginner"
        d <= 4  -> "Easy"
        d <= 6  -> "Intermediate"
        d <= 8  -> "Advanced"
        else    -> "Master"
    }}"

    // =========================================================================
    // Button wiring
    // =========================================================================

    private fun setupButtons() {
        // vs Computer — show color-pick dialog
        findViewById<Button>(R.id.btnNewComputer).setOnClickListener {
            showColorPickDialog()
        }
        findViewById<Button>(R.id.btnResumeComputer).setOnClickListener {
            launchGame(GameMode.COMPUTER, resume = true)
        }

        // vs Friend — show player-names dialog (P1 always White at bottom)
        findViewById<Button>(R.id.btnNewFriend).setOnClickListener {
            showPlayerNamesDialog()
        }
        findViewById<Button>(R.id.btnResumeFriend).setOnClickListener {
            launchGame(GameMode.FRIEND, resume = true)
        }

        // Online
        findViewById<Button>(R.id.btnOnline).setOnClickListener {
            startActivity(Intent(this, OnlineActivity::class.java))
        }
    }

    private fun updateResumeButtons() {
        val resumeComputer = findViewById<Button>(R.id.btnResumeComputer)
        val resumeFriend   = findViewById<Button>(R.id.btnResumeFriend)
        resumeComputer.isEnabled = storage.hasSavedGame(GameMode.COMPUTER)
        resumeComputer.alpha     = if (resumeComputer.isEnabled) 1f else 0.38f
        resumeFriend.isEnabled   = storage.hasSavedGame(GameMode.FRIEND)
        resumeFriend.alpha       = if (resumeFriend.isEnabled)   1f else 0.38f
    }

    // =========================================================================
    // Color-pick dialog (vs Computer)
    // =========================================================================

    private fun showColorPickDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_color_pick)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)

        dialog.findViewById<LinearLayout>(R.id.optionWhite).setOnClickListener {
            dialog.dismiss()
            storage.clearGame(GameMode.COMPUTER)
            launchGame(GameMode.COMPUTER, resume = false, playerIsWhite = true)
        }
        dialog.findViewById<LinearLayout>(R.id.optionBlack).setOnClickListener {
            dialog.dismiss()
            storage.clearGame(GameMode.COMPUTER)
            launchGame(GameMode.COMPUTER, resume = false, playerIsWhite = false)
        }
        dialog.findViewById<TextView>(R.id.btnCancelColorPick).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // =========================================================================
    // Player-names dialog (vs Friend)
    // =========================================================================

    private fun showPlayerNamesDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_player_names)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)

        val etP1 = dialog.findViewById<EditText>(R.id.etP1Name)
        val etP2 = dialog.findViewById<EditText>(R.id.etP2Name)

        dialog.findViewById<Button>(R.id.btnStartGame).setOnClickListener {
            val p1 = etP1.text.toString().trim().ifEmpty { "Player 1" }
            val p2 = etP2.text.toString().trim().ifEmpty { "Player 2" }
            dialog.dismiss()
            storage.clearGame(GameMode.FRIEND)
            launchGame(
                mode          = GameMode.FRIEND,
                resume        = false,
                playerIsWhite = true,   // P1 always plays White at the bottom
                p1Name        = p1,
                p2Name        = p2
            )
        }
        dialog.findViewById<TextView>(R.id.btnCancelNames).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // =========================================================================
    // Launch helper
    // =========================================================================

    private fun launchGame(
        mode: GameMode,
        resume: Boolean,
        playerIsWhite: Boolean = true,
        p1Name: String = "Player 1",
        p2Name: String = "Player 2"
    ) {
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_MODE,         mode.name)
            putExtra(GameActivity.EXTRA_DIFFICULTY,   selectedDifficulty)
            putExtra(GameActivity.EXTRA_RESUME,       resume)
            putExtra(GameActivity.EXTRA_PLAYER_WHITE, playerIsWhite)
            putExtra(GameActivity.EXTRA_P1_NAME,      p1Name)
            putExtra(GameActivity.EXTRA_P2_NAME,      p2Name)
        }
        startActivity(intent)
    }
}
