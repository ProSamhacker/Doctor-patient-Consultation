package com.example.hospitalmanagement

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class RoleSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        findViewById<CardView>(R.id.btnDoctor).setOnClickListener {
            // Navigate to Doctor Dashboard (We will build this next)
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("USER_ROLE", "DOCTOR")
            startActivity(intent)
        }

        findViewById<CardView>(R.id.btnPatient).setOnClickListener {
            // Navigate to Patient Dashboard
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("USER_ROLE", "PATIENT")
            startActivity(intent)
        }
    }
}