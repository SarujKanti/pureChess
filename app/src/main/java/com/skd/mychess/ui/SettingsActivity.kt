package com.skd.mychess.ui

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.skd.mychess.R
import com.skd.mychess.storage.SettingsManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsManager

    // Sound widgets
    private lateinit var switchMaster:  SwitchMaterial
    private lateinit var switchMove:    SwitchMaterial
    private lateinit var switchCapture: SwitchMaterial
    private lateinit var switchCheck:   SwitchMaterial
    private lateinit var switchWin:     SwitchMaterial
    private lateinit var soundSubOptions: LinearLayout

    // Theme buttons
    private lateinit var btnThemeSystem: LinearLayout
    private lateinit var btnThemeLight:  LinearLayout
    private lateinit var btnThemeDark:   LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsManager(this)
        settings.applyAppTheme()
        setContentView(R.layout.activity_settings)

        // Match status bar icon style to current theme (dark icons on light bg)
        val isNight = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = !isNight

        bindViews()
        populateBoardThemes()
        populatePieceStyles()
        populateBgThemes()
        setupSoundToggles()
        setupAppThemeButtons()
    }

    // =========================================================================
    // View binding
    // =========================================================================

    private fun bindViews() {
        findViewById<View>(R.id.btnBackSettings).setOnClickListener { finish() }

        switchMaster  = findViewById(R.id.switchSoundMaster)
        switchMove    = findViewById(R.id.switchSoundMove)
        switchCapture = findViewById(R.id.switchSoundCapture)
        switchCheck   = findViewById(R.id.switchSoundCheck)
        switchWin     = findViewById(R.id.switchSoundWin)
        soundSubOptions = findViewById(R.id.soundSubOptions)

        btnThemeSystem = findViewById(R.id.btnThemeSystem)
        btnThemeLight  = findViewById(R.id.btnThemeLight)
        btnThemeDark   = findViewById(R.id.btnThemeDark)
    }

    // =========================================================================
    // Sound toggles
    // =========================================================================

    private fun setupSoundToggles() {
        switchMaster.isChecked  = settings.soundEnabled
        switchMove.isChecked    = settings.soundMove
        switchCapture.isChecked = settings.soundCapture
        switchCheck.isChecked   = settings.soundCheck
        switchWin.isChecked     = settings.soundWin

        updateSubOptionsEnabled(settings.soundEnabled)

        switchMaster.setOnCheckedChangeListener { _, on ->
            settings.soundEnabled = on
            updateSubOptionsEnabled(on)
        }
        switchMove.setOnCheckedChangeListener    { _, on -> settings.soundMove    = on }
        switchCapture.setOnCheckedChangeListener { _, on -> settings.soundCapture = on }
        switchCheck.setOnCheckedChangeListener   { _, on -> settings.soundCheck   = on }
        switchWin.setOnCheckedChangeListener     { _, on -> settings.soundWin     = on }
    }

    private fun updateSubOptionsEnabled(enabled: Boolean) {
        soundSubOptions.alpha = if (enabled) 1f else 0.38f
        for (i in 0 until soundSubOptions.childCount) {
            soundSubOptions.getChildAt(i).isEnabled = enabled
        }
    }

    // =========================================================================
    // Board themes
    // =========================================================================

    private fun populateBoardThemes() {
        val row1 = findViewById<LinearLayout>(R.id.boardThemeRow1)
        val row2 = findViewById<LinearLayout>(R.id.boardThemeRow2)
        row1.removeAllViews()
        row2.removeAllViews()

        SettingsManager.BOARD_THEMES.forEachIndexed { index, (name, light, dark) ->
            val swatch = makeBoardSwatch(index, name, light, dark)
            if (index < 5) row1.addView(swatch) else row2.addView(swatch)
        }
    }

    private fun makeBoardSwatch(index: Int, name: String, light: Int, dark: Int): View {
        val selected = settings.boardTheme == index
        val gold     = Color.parseColor("#D4AF37")

        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(4), 0, dp(4), dp(8))
        }

        // ── Outer FrameLayout carries the selection border ─────────────
        // The board cells fill the inner area completely, so the stroke MUST
        // live on a wrapper that sits outside the cells — not on the board itself.
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            background   = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(9).toFloat()
                setColor(Color.TRANSPARENT)
                if (selected)
                    setStroke(dp(3), gold)
                else
                    setStroke(dp(1), Color.parseColor("#33D4AF37"))
            }
            // Inset so the board sits inside the border ring
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        // ── Mini 2×2 chess-board preview ───────────────────────────────
        val board = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
            // Clip cells to rounded corners
            background   = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(light)
            }
            clipToOutline = true
        }

        val row1v = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val row2v = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        listOf(light, dark, dark, light).forEachIndexed { i, color ->
            val cell = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setBackgroundColor(color)
            }
            if (i < 2) row1v.addView(cell) else row2v.addView(cell)
        }
        board.addView(row1v)
        board.addView(row2v)
        frame.addView(board)
        container.addView(frame)

        // ── Label ──────────────────────────────────────────────────────
        val label = TextView(this).apply {
            text      = name
            textSize  = 10f
            gravity   = Gravity.CENTER
            setTextColor(if (selected) gold else Color.parseColor("#B0B8D0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(4)
            }
        }
        container.addView(label)

        container.setOnClickListener {
            settings.boardTheme = index
            populateBoardThemes()
        }
        return container
    }

    // =========================================================================
    // Piece styles
    // =========================================================================

    private fun populatePieceStyles() {
        val row = findViewById<LinearLayout>(R.id.pieceStyleRow)
        row.removeAllViews()

        val previews = listOf("♔ Classic", "♔ Warm", "♔ Ice")
        val tints    = listOf<Int?>(null, 0xFFFFAA44.toInt(), 0xFF44AAFF.toInt())

        previews.forEachIndexed { index, label ->
            val selected = settings.pieceStyle == index
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f)
                    .also { it.marginEnd = if (index < 2) dp(8) else 0 }
                background  = GradientDrawable().apply {
                    shape         = GradientDrawable.RECTANGLE
                    cornerRadius  = dp(10).toFloat()
                    setColor(Color.parseColor("#1E2538"))
                    if (selected) setStroke(dp(2), Color.parseColor("#D4AF37"))
                }
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }

            val icon = TextView(this).apply {
                text      = "♔"
                textSize  = 24f
                gravity   = Gravity.CENTER
                tints[index]?.let { setTextColor(it) }
                    ?: setTextColor(Color.WHITE)
            }
            val txt = TextView(this).apply {
                text      = label.split(" ")[1]
                textSize  = 11f
                gravity   = Gravity.CENTER
                setTextColor(if (selected) Color.parseColor("#D4AF37") else Color.parseColor("#B0B8D0"))
            }
            card.addView(icon); card.addView(txt)
            card.setOnClickListener {
                settings.pieceStyle = index
                populatePieceStyles()
            }
            row.addView(card)
        }
    }

    // =========================================================================
    // Background themes
    // =========================================================================

    private fun populateBgThemes() {
        val row1 = findViewById<LinearLayout>(R.id.bgThemeRow1)
        val row2 = findViewById<LinearLayout>(R.id.bgThemeRow2)
        row1.removeAllViews()
        row2.removeAllViews()

        SettingsManager.HOME_BACKGROUNDS.forEachIndexed { index, (name, start, end) ->
            val swatch = makeBgSwatch(index, name, start, end)
            if (index < 5) row1.addView(swatch) else row2.addView(swatch)
        }
    }

    private fun makeBgSwatch(index: Int, name: String, start: Int, end: Int): View {
        val selected = settings.homeBackground == index
        val gold     = Color.parseColor("#D4AF37")

        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(4), 0, dp(4), dp(8))
        }

        // ── Outer frame carries the selection border ───────────────────
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            background   = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(11).toFloat()
                setColor(Color.TRANSPARENT)
                if (selected)
                    setStroke(dp(3), gold)
                else
                    setStroke(dp(1), Color.parseColor("#33D4AF37"))
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        // ── Gradient preview ───────────────────────────────────────────
        val preview = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)
            ).apply { cornerRadius = dp(7).toFloat() }
        }
        frame.addView(preview)
        container.addView(frame)

        // ── Label ──────────────────────────────────────────────────────
        val label = TextView(this).apply {
            text      = name
            textSize  = 10f
            gravity   = Gravity.CENTER
            setTextColor(if (selected) gold else Color.parseColor("#B0B8D0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(4)
            }
        }
        container.addView(label)

        container.setOnClickListener {
            settings.homeBackground = index
            populateBgThemes()
        }
        return container
    }

    // =========================================================================
    // App theme buttons
    // =========================================================================

    private fun setupAppThemeButtons() {
        refreshThemeButtons()

        btnThemeSystem.setOnClickListener {
            settings.appTheme = 0
            settings.applyAppTheme()
            refreshThemeButtons()
            recreate()
        }
        btnThemeLight.setOnClickListener {
            settings.appTheme = 1
            settings.applyAppTheme()
            refreshThemeButtons()
            recreate()
        }
        btnThemeDark.setOnClickListener {
            settings.appTheme = 2
            settings.applyAppTheme()
            refreshThemeButtons()
            recreate()
        }
    }

    private fun refreshThemeButtons() {
        val gold = Color.parseColor("#D4AF37")
        listOf(btnThemeSystem, btnThemeLight, btnThemeDark).forEachIndexed { i, btn ->
            val active = settings.appTheme == i
            (btn.background as? GradientDrawable)?.setStroke(dp(2),
                if (active) gold else Color.TRANSPARENT)
            // Tint text child
            (btn.getChildAt(1) as? TextView)?.setTextColor(
                if (active) gold else 0xFFB0B8D0.toInt())
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
