package com.skd.mychess.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
 *     host:        String   (device ID / player name of creator)
 *     guest:       String?  (null until someone joins)
 *     status:      "waiting" | "ready"
 *     timeMinutes: Int      (chosen by host; 0 = no timer)
 *     moves:       List<String>
 *     createdAt:   Timestamp
 *
 * Time control is shown ONLY to the host (Create Room flow).
 * The joiner simply reads timeMinutes from the room document.
 */
class OnlineActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private var roomListener: ListenerRegistration? = null
    private var currentRoomCode: String? = null
    private var timeMinutes = 0   // set by host after time dialog; guest reads from Firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online)
        // NOTE: timeMinutes is NOT read from intent here — the host picks it via dialog,
        //       and the guest reads it from the room document when joining.

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        setupCreateRoom()
        setupJoinRoom()
    }

    override fun onDestroy() {
        super.onDestroy()
        roomListener?.remove()
    }

    // =========================================================================
    // Create room  (host picks time → room is created → wait for guest)
    // =========================================================================

    private fun setupCreateRoom() {
        val roomCodeBox  = findViewById<View>(R.id.roomCodeBox)
        val txtRoomCode  = findViewById<TextView>(R.id.txtRoomCode)
        val btnCreate    = findViewById<Button>(R.id.btnCreateRoom)
        val progress     = findProgress()

        btnCreate.setOnClickListener {
            // Step 1: host picks time control
            showTimeControlDialog { minutes ->
                timeMinutes = minutes

                // Step 2: create the Firestore room
                val code = generateRoomCode()
                currentRoomCode = code
                setLoading(true, progress, btnCreate)

                val room = hashMapOf(
                    "host"        to "Player 1",
                    "guest"       to null,
                    "status"      to "waiting",
                    "timeMinutes" to timeMinutes,          // stored so guest can read it
                    "moves"       to listOf<String>(),     // move log for real-time sync
                    "createdAt"   to FieldValue.serverTimestamp()
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
    }

    /** Watch Firestore until a guest joins, then start the game. */
    private fun listenForGuest(code: String) {
        roomListener = db.collection("rooms").document(code)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val status = snap.getString("status")
                if (status == "ready") {
                    roomListener?.remove()
                    launchOnlineGame(code, isHost = true, timeMinutes = timeMinutes)
                }
            }
    }

    // =========================================================================
    // Join room  (no time dialog — reads timeMinutes from Firestore room doc)
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

                    // Read the time the host chose — guest uses the same value
                    val roomTime = (snap.getLong("timeMinutes") ?: 0L).toInt()

                    // Mark room as ready so the host's listener fires
                    snap.reference.update(
                        mapOf("guest" to "Player 2", "status" to "ready")
                    ).addOnSuccessListener {
                        setLoading(false, progress, btnJoin)
                        launchOnlineGame(code, isHost = false, timeMinutes = roomTime)
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
    // Time control dialog (shown only to the host before creating a room)
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
    // Helpers
    // =========================================================================

    private fun launchOnlineGame(roomCode: String, isHost: Boolean, timeMinutes: Int) {
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
