package com.example.hackathon

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        auth = FirebaseAuth.getInstance()

        val email = findViewById<EditText>(R.id.email)
        val resetPasswordButton = findViewById<Button>(R.id.resetPasswordButton)
        val backToLoginText = findViewById<TextView>(R.id.backToLoginText)

        resetPasswordButton.setOnClickListener {
            val emailText = email.text.toString()

            if (emailText.isNotEmpty()) {
                auth.sendPasswordResetEmail(emailText)
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            Toast.makeText(this, "Reset link sent to your email.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Error.", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show()
            }
        }

        backToLoginText.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }
}