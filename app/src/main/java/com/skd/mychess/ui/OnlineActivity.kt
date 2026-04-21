package com.skd.mychess.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.skd.mychess.R
import com.skd.mychess.model.GameMode
import kotlin.random.Random

/**
 * Online lobby — lets one player create a room (gets a 6-char code) and
 * another player join using that code. Both sides are then navigated into
 * GameActivity in ONLINE mode.
 *
 * Firestore schema:
 *   rooms/{roomCode}
 *     host:      String   (device ID / player name of creator)
 *     guest:     String?  (null until someone joins)
 *     status:    "waiting" | "ready"
 *     createdAt: Timestamp
 */
class OnlineActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private var roomListener: ListenerRegistration? = null
    private var currentRoomCode: String? = null
    private var timeMinutes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online)
        timeMinutes = intent.getIntExtra(GameActivity.EXTRA_TIME_MINUTES, 0)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        setupCreateRoom()
        setupJoinRoom()
    }

    override fun onDestroy() {
        super.onDestroy()
        roomListener?.remove()
    }

    // =========================================================================
    // Create room
    // =========================================================================

    private fun setupCreateRoom() {
        val roomCodeBox = findViewById<View>(R.id.roomCodeBox)
        val txtRoomCode  = findViewById<TextView>(R.id.txtRoomCode)
        val btnCreate    = findViewById<Button>(R.id.btnCreateRoom)
        val progress     = findProgress()

        btnCreate.setOnClickListener {
            val code = generateRoomCode()
            currentRoomCode = code

            setLoading(true, progress, btnCreate)

            val room = hashMapOf(
                "host"      to "Player 1",
                "guest"     to null,
                "status"    to "waiting",
                "moves"     to listOf<String>(),      // move log for real-time sync
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.collection("rooms").document(code)
                .set(room)
                .addOnSuccessListener {
                    txtRoomCode.text = code
                    roomCodeBox.visibility = View.VISIBLE
                    setLoading(false, progress, btnCreate)
                    listenForGuest(code)
                }
                .addOnFailureListener { e ->
                    setLoading(false, progress, btnCreate)
                    toast("Failed to create room: ${e.message}")
                }
        }
    }

    /** Watch Firestore until a guest joins, then start the game. */
    private fun listenForGuest(code: String) {
        roomListener = db.collection("rooms").document(code)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val status = snap.getString("status")
                if (status == "ready") {
                    roomListener?.remove()
                    launchOnlineGame(code, isHost = true)
                }
            }
    }

    // =========================================================================
    // Join room
    // =========================================================================

    private fun setupJoinRoom() {
        val etCode   = findViewById<EditText>(R.id.etRoomCode)
        val btnJoin  = findViewById<Button>(R.id.btnJoinRoom)
        val progress = findProgress()

        btnJoin.setOnClickListener {
            val code = etCode.text.toString().trim().uppercase()
            if (code.length != 6) {
                toast("Please enter a 6-character room code")
                return@setOnClickListener
            }

            setLoading(true, progress, btnJoin)

            db.collection("rooms").document(code).get()
                .addOnSuccessListener { snap ->
                    if (!snap.exists()) {
                        setLoading(false, progress, btnJoin)
                        toast("Room \"$code\" not found")
                        return@addOnSuccessListener
                    }
                    if (snap.getString("status") != "waiting") {
                        setLoading(false, progress, btnJoin)
                        toast("Room is already full or closed")
                        return@addOnSuccessListener
                    }
                    // Mark room as ready so the host's listener fires
                    snap.reference.update(
                        mapOf("guest" to "Player 2", "status" to "ready")
                    ).addOnSuccessListener {
                        setLoading(false, progress, btnJoin)
                        launchOnlineGame(code, isHost = false)
                    }.addOnFailureListener { e ->
                        setLoading(false, progress, btnJoin)
                        toast("Could not join: ${e.message}")
                    }
                }
                .addOnFailureListener { e ->
                    setLoading(false, progress, btnJoin)
                    toast("Error: ${e.message}")
                }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun launchOnlineGame(roomCode: String, isHost: Boolean) {
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_MODE,         GameMode.ONLINE.name)
            putExtra(GameActivity.EXTRA_PLAYER_WHITE, isHost)   // host = white, guest = black
            putExtra(GameActivity.EXTRA_RESUME,       false)
            putExtra(GameActivity.EXTRA_ROOM_CODE,    roomCode)
            putExtra(GameActivity.EXTRA_TIME_MINUTES, timeMinutes)
        }
        startActivity(intent)
        finish()
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    /** Returns the ProgressBar if it exists in the layout (optional view). */
    private fun findProgress(): ProgressBar? =
        try { findViewById(R.id.progressOnline) } catch (_: Exception) { null }

    private fun setLoading(loading: Boolean, progress: ProgressBar?, btn: Button) {
        progress?.visibility = if (loading) View.VISIBLE else View.GONE
        btn.isEnabled = !loading
        btn.alpha = if (loading) 0.5f else 1f
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
