package com.skd.mychess.engine

import android.media.AudioManager
import android.media.ToneGenerator
import com.skd.mychess.storage.SettingsManager

/**
 * Plays in-game sound effects using Android's built-in [ToneGenerator].
 * No external audio assets required.
 *
 * Each play() call spawns a short-lived daemon thread so the UI never blocks.
 */
class SoundManager(private val settings: SettingsManager) {

    // ── Public play methods ───────────────────────────────────────────────────

    /** Short soft click — played after every normal piece move. */
    fun playMove() {
        if (!settings.soundEnabled || !settings.soundMove) return
        playTone(ToneGenerator.TONE_PROP_ACK, 80)
    }

    /** Slightly richer tone — played when a piece is captured. */
    fun playCapture() {
        if (!settings.soundEnabled || !settings.soundCapture) return
        playTone(ToneGenerator.TONE_CDMA_HIGH_SL, 120)
    }

    /** Alert double-beep — played when the king is in check. */
    fun playCheck() {
        if (!settings.soundEnabled || !settings.soundCheck) return
        playTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 400)
    }

    /** Celebratory multi-tone — played on checkmate (winner's device). */
    fun playWin() {
        if (!settings.soundEnabled || !settings.soundWin) return
        playTone(ToneGenerator.TONE_CDMA_CONFIRM, 900)
    }

    /** Two-note chime — played when castling. */
    fun playCastle() {
        if (!settings.soundEnabled || !settings.soundMove) return
        playTone(ToneGenerator.TONE_PROP_BEEP2, 150)
    }

    /** Short stalemate buzz — played when the game is a draw. */
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
            } finally {
                tg?.release()
            }
        }
        t.isDaemon = true
        t.start()
    }
}
