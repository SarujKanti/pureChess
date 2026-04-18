package com.skd.mychess.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
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

    private fun setupDifficultySeekBar() {
        val seekBar = findViewById<SeekBar>(R.id.seekDifficulty)
        val label = findViewById<TextView>(R.id.txtDifficultyLabel)

        selectedDifficulty = storage.loadDifficulty().coerceIn(1, 10)
        seekBar.progress = selectedDifficulty - 1

        label.text = difficultyLabel(selectedDifficulty)

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

    private fun setupButtons() {
        findViewById<Button>(R.id.btnNewComputer).setOnClickListener {
            storage.clearGame(GameMode.COMPUTER)
            launchGame(GameMode.COMPUTER, resume = false)
        }
        findViewById<Button>(R.id.btnResumeComputer).setOnClickListener {
            launchGame(GameMode.COMPUTER, resume = true)
        }
        findViewById<Button>(R.id.btnNewFriend).setOnClickListener {
            storage.clearGame(GameMode.FRIEND)
            launchGame(GameMode.FRIEND, resume = false)
        }
        findViewById<Button>(R.id.btnResumeFriend).setOnClickListener {
            launchGame(GameMode.FRIEND, resume = true)
        }
        findViewById<Button>(R.id.btnOnline).setOnClickListener {
            startActivity(Intent(this, OnlineActivity::class.java))
        }
    }

    private fun updateResumeButtons() {
        val resumeComputer = findViewById<Button>(R.id.btnResumeComputer)
        val resumeFriend = findViewById<Button>(R.id.btnResumeFriend)
        resumeComputer.isEnabled = storage.hasSavedGame(GameMode.COMPUTER)
        resumeComputer.alpha = if (resumeComputer.isEnabled) 1f else 0.4f
        resumeFriend.isEnabled = storage.hasSavedGame(GameMode.FRIEND)
        resumeFriend.alpha = if (resumeFriend.isEnabled) 1f else 0.4f
    }

    private fun launchGame(mode: GameMode, resume: Boolean) {
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_MODE, mode.name)
            putExtra(GameActivity.EXTRA_DIFFICULTY, selectedDifficulty)
            putExtra(GameActivity.EXTRA_RESUME, resume)
        }
        startActivity(intent)
    }
}
