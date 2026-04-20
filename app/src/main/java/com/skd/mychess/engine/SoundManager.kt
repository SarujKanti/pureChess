package com.skd.mychess.engine

import android.media.AudioManager
import android.media.ToneGenerator
import com.skd.mychess.storage.SettingsManager

/**
 * Plays in-game sound effects using Android's built-in [ToneGenerator].
 * No external audio assets required.
 *
 * Only constants that are part of the public Android SDK are used here.
 * Each play() call spawns a short-lived daemon thread so the UI never blocks.
 */
class SoundManager(private val settings: SettingsManager) {

    // ── Public play methods ───────────────────────────────────────────────────

    /** Short click — played after every normal piece move. */
    fun playMove() {
        if (!settings.soundEnabled || !settings.soundMove) return
        playTone(ToneGenerator.TONE_PROP_ACK, 80)
    }

    /** Double-beep — played when a piece is captured. */
    fun playCapture() {
        if (!settings.soundEnabled || !settings.soundCapture) return
        playTone(ToneGenerator.TONE_PROP_BEEP2, 120)
    }

    /** Urgent alert — played when the king is in check. */
    fun playCheck() {
        if (!settings.soundEnabled || !settings.soundCheck) return
        playTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 400)
    }

    /** Confirmation tone — played on checkmate (winner's device). */
    fun playWin() {
        if (!settings.soundEnabled || !settings.soundWin) return
        playTone(ToneGenerator.TONE_CDMA_CONFIRM, 900)
    }

    /** Prompt beep — played when castling. */
    fun playCastle() {
        if (!settings.soundEnabled || !settings.soundMove) return
        playTone(ToneGenerator.TONE_PROP_PROMPT, 150)
    }

    /** NACK buzz — played when the game is a draw. */
    fun playDraw() {
        if (!settings.soundEnabled || !settings.soundWin) return
        playTone(ToneGenerator.TONE_PROP_NACK, 400)
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun playTone(tone: Int, durationMs: Int) {
        val t = Thread {
            var tg: ToneGenerator? = null
            try {
                tg = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
                tg.startTone(tone, durationMs)
                Thread.sleep((durationMs + 80).toLong())
            } catch (_: Exception) {
                // Ignore — audio is optional, never crash the game
            } finally {
                tg?.release()
            }
        }
        t.isDaemon = true
        t.start()
    }
}
