package com.skd.mychess.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.skd.mychess.R

/**
 * Full-screen branded splash screen shown on every cold start.
 * Animates the logo (scale overshoot), title (slide-up), tagline (fade),
 * and "Powered by SKD" footer (fade), then transitions to HomeActivity.
 *
 * Total on-screen time ≈ 2.1 seconds.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive full-screen — hide both status and nav bars for the splash
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContentView(R.layout.activity_splash)

        // Deep navy-to-dark-blue gradient — always dark for splash branding
        findViewById<View>(R.id.splashBackground).background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF0A0F1E.toInt(), 0xFF12213D.toInt())
        )

        val logo        = findViewById<ImageView>(R.id.splashLogo)
        val title       = findViewById<TextView>(R.id.splashTitle)
        val tagline     = findViewById<TextView>(R.id.splashTagline)
        val poweredLayout = findViewById<LinearLayout>(R.id.splashPoweredLayout)

        // ── Set initial (invisible) state ────────────────────────────────────
        logo.scaleX = 0.35f
        logo.scaleY = 0.35f
        logo.alpha  = 0f

        title.translationY = 50f
        title.alpha = 0f

        tagline.alpha = 0f
        poweredLayout.alpha = 0f

        // ── Logo: scale up with overshoot + fade in ──────────────────────────
        val logoScaleX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.35f, 1.0f).apply {
            duration = 650
            interpolator = OvershootInterpolator(1.4f)
        }
        val logoScaleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.35f, 1.0f).apply {
            duration = 650
            interpolator = OvershootInterpolator(1.4f)
        }
        val logoFade = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f).apply {
            duration = 450
            interpolator = DecelerateInterpolator()
        }

        // ── Title: slide up + fade in ────────────────────────────────────────
        val titleSlide = ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, 50f, 0f).apply {
            duration  = 500
            startDelay = 280
            interpolator = DecelerateInterpolator(1.5f)
        }
        val titleFade = ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f).apply {
            duration  = 500
            startDelay = 280
            interpolator = DecelerateInterpolator()
        }

        // ── Tagline: fade in ─────────────────────────────────────────────────
        val taglineFade = ObjectAnimator.ofFloat(tagline, View.ALPHA, 0f, 1f).apply {
            duration  = 400
            startDelay = 550
            interpolator = DecelerateInterpolator()
        }

        // ── "Powered by SKD": fade in last ───────────────────────────────────
        val poweredFade = ObjectAnimator.ofFloat(poweredLayout, View.ALPHA, 0f, 1f).apply {
            duration  = 400
            startDelay = 750
            interpolator = DecelerateInterpolator()
        }

        // ── Run all animations together, navigate when done ──────────────────
        AnimatorSet().apply {
            playTogether(
                logoScaleX, logoScaleY, logoFade,
                titleSlide, titleFade,
                taglineFade,
                poweredFade
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Brief pause so the user can appreciate the full screen
                    window.decorView.postDelayed({ navigateToHome() }, 750)
                }
            })
            start()
        }
    }

    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
