package com.skd.mychess.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.skd.mychess.R
import kotlin.random.Random

class OnlineActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        setupCreateRoom()
        setupJoinRoom()
    }

    private fun setupCreateRoom() {
        val roomCodeBox = findViewById<android.view.View>(R.id.roomCodeBox)
        val txtRoomCode = findViewById<TextView>(R.id.txtRoomCode)

        findViewById<Button>(R.id.btnCreateRoom).setOnClickListener {
            val code = generateRoomCode()
            txtRoomCode.text = code
            roomCodeBox.visibility = android.view.View.VISIBLE

            // TODO: Connect to Firebase and create room with this code
            Toast.makeText(this,
                "Firebase not configured yet.\nSee instructions to enable online play.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun setupJoinRoom() {
        val etCode = findViewById<EditText>(R.id.etRoomCode)

        findViewById<Button>(R.id.btnJoinRoom).setOnClickListener {
            val code = etCode.text.toString().trim().uppercase()
            if (code.length != 6) {
                Toast.makeText(this, "Please enter a 6-digit room code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // TODO: Connect to Firebase and join room with this code
            Toast.makeText(this,
                "Firebase not configured yet.\nSee instructions to enable online play.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
