package com.skd.mychess.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * Central store for all user preferences (sound, board theme, piece style,
 * home background, app light/dark theme).
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("PureChessSettings", Context.MODE_PRIVATE)

    // ── Sound ─────────────────────────────────────────────────────────────────
    var soundEnabled: Boolean
        get()      = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var soundMove: Boolean
        get()      = prefs.getBoolean(KEY_SOUND_MOVE, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_MOVE, value).apply()

    var soundCapture: Boolean
        get()      = prefs.getBoolean(KEY_SOUND_CAPTURE, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_CAPTURE, value).apply()

    var soundCheck: Boolean
        get()      = prefs.getBoolean(KEY_SOUND_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_CHECK, value).apply()

    var soundWin: Boolean
        get()      = prefs.getBoolean(KEY_SOUND_WIN, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_WIN, value).apply()

    // ── Board theme ───────────────────────────────────────────────────────────
    var boardTheme: Int
        get()      = prefs.getInt(KEY_BOARD_THEME, 0)
        set(value) = prefs.edit().putInt(KEY_BOARD_THEME, value).apply()

    // ── Piece style ───────────────────────────────────────────────────────────
    var pieceStyle: Int                  // 0=Classic  1=Warm  2=Ice
        get()      = prefs.getInt(KEY_PIECE_STYLE, 0)
        set(value) = prefs.edit().putInt(KEY_PIECE_STYLE, value).apply()

    // ── Home background ───────────────────────────────────────────────────────
    var homeBackground: Int
        get()      = prefs.getInt(KEY_HOME_BG, 0)
        set(value) = prefs.edit().putInt(KEY_HOME_BG, value).apply()

    // ── App theme ─────────────────────────────────────────────────────────────
    // 0 = System default   1 = Light   2 = Dark
    var appTheme: Int
        get()      = prefs.getInt(KEY_APP_THEME, 0)
        set(value) = prefs.edit().putInt(KEY_APP_THEME, value).apply()

    fun applyAppTheme() {
        val mode = when (appTheme) {
            1    -> AppCompatDelegate.MODE_NIGHT_NO
            2    -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    // =========================================================================
    // Theme data
    // =========================================================================

    companion object {
        private const val KEY_SOUND_ENABLED  = "sound_enabled"
        private const val KEY_SOUND_MOVE     = "sound_move"
        private const val KEY_SOUND_CAPTURE  = "sound_capture"
        private const val KEY_SOUND_CHECK    = "sound_check"
        private const val KEY_SOUND_WIN      = "sound_win"
        private const val KEY_BOARD_THEME    = "board_theme"
        private const val KEY_PIECE_STYLE    = "piece_style"
        private const val KEY_HOME_BG        = "home_bg"
        private const val KEY_APP_THEME      = "app_theme"

        // ── Board themes: Triple(name, lightSquareColor, darkSquareColor) ─────
        val BOARD_THEMES = listOf(
            Triple("Classic",   0xFFF0D9B5.toInt(), 0xFFB58863.toInt()),
            Triple("Ocean",     0xFFDEF0FF.toInt(), 0xFF4A90D9.toInt()),
            Triple("Forest",    0xFFD4E8C2.toInt(), 0xFF4A7C59.toInt()),
            Triple("Coral",     0xFFFFDDD2.toInt(), 0xFFC47A67.toInt()),
            Triple("Midnight",  0xFFC8D8E8.toInt(), 0xFF2C3E50.toInt()),
            Triple("Lavender",  0xFFE8E0F0.toInt(), 0xFF7E57C2.toInt()),
            Triple("Emerald",   0xFFD5F5E3.toInt(), 0xFF1E8449.toInt()),
            Triple("Sunset",    0xFFFAE5D3.toInt(), 0xFFD35400.toInt()),
            Triple("Steel",     0xFFD5D8DC.toInt(), 0xFF566573.toInt()),
            Triple("Rosewood",  0xFFFADBD8.toInt(), 0xFFC0392B.toInt()),
        )

        // ── Piece style names ─────────────────────────────────────────────────
        val PIECE_STYLES = listOf("Classic", "Warm", "Ice")

        // ── Home background gradients (dark mode): Triple(name, startColor, endColor) ─
        val HOME_BACKGROUNDS = listOf(
            Triple("Midnight",  0xFF0D1B2A.toInt(), 0xFF1B4965.toInt()),
            Triple("Purple",    0xFF2D1B69.toInt(), 0xFF4A1860.toInt()),
            Triple("Forest",    0xFF0D3B2E.toInt(), 0xFF1B5E42.toInt()),
            Triple("Teal",      0xFF003040.toInt(), 0xFF005F73.toInt()),
            Triple("Burgundy",  0xFF2D0A1A.toInt(), 0xFF6B1A35.toInt()),
            Triple("Bronze",    0xFF3D1C00.toInt(), 0xFF7B3F00.toInt()),
            Triple("Slate",     0xFF1A1A2E.toInt(), 0xFF2C3A6B.toInt()),
            Triple("Crimson",   0xFF2D0000.toInt(), 0xFF7B0000.toInt()),
            Triple("Navy Blue", 0xFF0A1628.toInt(), 0xFF1E3A5F.toInt()),
            Triple("Olive",     0xFF1A2200.toInt(), 0xFF3D5200.toInt()),
        )

        // ── Home background gradients (light mode): soft pastel versions ───────
        val HOME_BACKGROUNDS_LIGHT = listOf(
            Triple("Pearl",     0xFFCDD8F0.toInt(), 0xFFABC0E8.toInt()),
            Triple("Lavender",  0xFFD4C8F0.toInt(), 0xFFBBA8E8.toInt()),
            Triple("Mint",      0xFFBCDDD0.toInt(), 0xFF9CCABC.toInt()),
            Triple("Teal",      0xFFB0D4E0.toInt(), 0xFF8DC4D8.toInt()),
            Triple("Rose",      0xFFF0C8D8.toInt(), 0xFFE0A0BC.toInt()),
            Triple("Caramel",   0xFFF0D8B8.toInt(), 0xFFDDB890.toInt()),
            Triple("Slate",     0xFFC0C8E0.toInt(), 0xFFA0AEDD.toInt()),
            Triple("Blush",     0xFFF0C8C8.toInt(), 0xFFE0A0A0.toInt()),
            Triple("Sky",       0xFFB8D4F0.toInt(), 0xFF90C0F0.toInt()),
            Triple("Sage",      0xFFC8DCC0.toInt(), 0xFFAAC8A8.toInt()),
        )
    }
}
