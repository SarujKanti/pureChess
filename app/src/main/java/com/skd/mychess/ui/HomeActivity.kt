package com.skd.mychess.ui

import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.skd.mychess.R
import com.skd.mychess.model.GameMode
import com.skd.mychess.storage.LocalGameStorage
import com.skd.mychess.storage.SettingsManager

class HomeActivity : AppCompatActivity() {

    private lateinit var storage: LocalGameStorage
    private lateinit var settings: SettingsManager
    private var selectedDifficulty = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved app theme before setContentView
        settings = SettingsManager(this)
        settings.applyAppTheme()

        setContentView(R.layout.activity_home)
        storage = LocalGameStorage(this)

        // Fix status bar icon color: dark icons on light bg, light icons on dark bg
        val isNightOnCreate = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = !isNightOnCreate

        applyBackground()
        setupDifficultySeekBar()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        // Re-apply background in case user changed it in Settings
        applyBackground()
        updateResumeButtons()
    }

    // =========================================================================
    // Background
    // =========================================================================

    private fun applyBackground() {
        val bgView = findViewById<View>(R.id.homeBackground) ?: return
        val isNight = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val backgrounds = if (isNight) SettingsManager.HOME_BACKGROUNDS
                          else         SettingsManager.HOME_BACKGROUNDS_LIGHT
        val idx = settings.homeBackground.coerceIn(0, backgrounds.size - 1)
        val (_, start, end) = backgrounds[idx]
        bgView.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)
        )
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
        // Settings icon
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // vs Computer — time dialog → color pick → launch
        findViewById<Button>(R.id.btnNewComputer).setOnClickListener {
            showTimeControlDialog { minutes -> showColorPickDialog(minutes) }
        }
        findViewById<Button>(R.id.btnResumeComputer).setOnClickListener {
            launchGame(GameMode.COMPUTER, resume = true)
        }

        // vs Friend — time dialog → player names → launch
        findViewById<Button>(R.id.btnNewFriend).setOnClickListener {
            showTimeControlDialog { minutes -> showPlayerNamesDialog(minutes) }
        }
        findViewById<Button>(R.id.btnResumeFriend).setOnClickListener {
            launchGame(GameMode.FRIEND, resume = true)
        }

        // Online — time dialog → online lobby
        findViewById<Button>(R.id.btnOnline).setOnClickListener {
            showTimeControlDialog { minutes ->
                val intent = Intent(this, OnlineActivity::class.java)
                intent.putExtra(GameActivity.EXTRA_TIME_MINUTES, minutes)
                startActivity(intent)
            }
        }
    }

    private fun updateResumeButtons() {
        val resumeComputer = findViewById<Button>(R.id.btnResumeComputer)
        val resumeFriend   = findViewById<Button>(R.id.btnResumeFriend)
        resumeComputer.isEnabled = storage.hasSavedGame(GameMode.COMPUTER)
        resumeComputer.alpha     = if (resumeComputer.isEnabled) 1f else 0.38f
        resumeFriend.isEnabled   = storage.hasSavedGame(GameMode.FRIEND)
        resumeFriend.alpha       = if (resumeFriend.isEnabled) 1f else 0.38f
    }

    // =========================================================================
    // Time control dialog (shown before every new game)
    // =========================================================================

    private fun showTimeControlDialog(onSelected: (Int) -> Unit) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_time_control)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)

        fun pick(minutes: Int) { dialog.dismiss(); onSelected(minutes) }
        dialog.findViewById<Button>(R.id.btn5min).setOnClickListener  { pick(5)  }
        dialog.findViewById<Button>(R.id.btn10min).setOnClickListener { pick(10) }
        dialog.findViewById<Button>(R.id.btn20min).setOnClickListener { pick(20) }
        dialog.findViewById<Button>(R.id.btn30min).setOnClickListener { pick(30) }
        dialog.findViewById<TextView>(R.id.btnNoTimer).setOnClickListener { pick(0) }
        dialog.show()
    }

    // =========================================================================
    // Color-pick dialog (vs Computer)
    // =========================================================================

    private fun showColorPickDialog(timeMinutes: Int) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_color_pick)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)

        dialog.findViewById<LinearLayout>(R.id.optionWhite).setOnClickListener {
            dialog.dismiss()
            storage.clearGame(GameMode.COMPUTER)
            launchGame(GameMode.COMPUTER, resume = false, playerIsWhite = true,
                timeMinutes = timeMinutes)
        }
        dialog.findViewById<LinearLayout>(R.id.optionBlack).setOnClickListener {
            dialog.dismiss()
            storage.clearGame(GameMode.COMPUTER)
            launchGame(GameMode.COMPUTER, resume = false, playerIsWhite = false,
                timeMinutes = timeMinutes)
        }
        dialog.findViewById<TextView>(R.id.btnCancelColorPick).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    // =========================================================================
    // Player-names dialog (vs Friend)
    // =========================================================================

    private fun showPlayerNamesDialog(timeMinutes: Int) {
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
                playerIsWhite = true,
                p1Name        = p1,
                p2Name        = p2,
                timeMinutes   = timeMinutes
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
        p2Name: String = "Player 2",
        timeMinutes: Int = 0
    ) {
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_MODE,         mode.name)
            putExtra(GameActivity.EXTRA_DIFFICULTY,   selectedDifficulty)
            putExtra(GameActivity.EXTRA_RESUME,       resume)
            putExtra(GameActivity.EXTRA_PLAYER_WHITE, playerIsWhite)
            putExtra(GameActivity.EXTRA_P1_NAME,      p1Name)
            putExtra(GameActivity.EXTRA_P2_NAME,      p2Name)
            putExtra(GameActivity.EXTRA_TIME_MINUTES, timeMinutes)
        }
        startActivity(intent)
    }
}
