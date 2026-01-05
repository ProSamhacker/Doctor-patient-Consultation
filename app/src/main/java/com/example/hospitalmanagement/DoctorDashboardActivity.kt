package com.example.hospitalmanagement

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.hospitalmanagement.FRAGMENTS.AppointmentsFragment
import com.example.hospitalmanagement.FRAGMENTS.DoctorHomeFragment
import com.example.hospitalmanagement.FRAGMENTS.MessagesFragment
import com.example.hospitalmanagement.FRAGMENTS.ProfileFragment
import com.example.hospitalmanagement.auth.AuthActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class DoctorDashboardActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var repository: HospitalRepository
    private var userId: String = ""
    private var userRole: String = "DOCTOR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_dashboard)

        userId = intent.getStringExtra("USER_ID") ?: ""
        userRole = intent.getStringExtra("USER_ROLE") ?: "DOCTOR"

        val database = AppDatabase.getDatabase(this)
        repository = HospitalRepository(
            database.doctorDao(), database.patientDao(), database.appointmentDao(),
            database.prescriptionDao(), database.messageDao(), database.consultationSessionDao(),
            database.aiExtractionDao(), database.medicalReportDao(), database.vitalSignsDao(),
            database.notificationDao(), database.emergencyContactDao(), database.medicationDao()
        )
        val factory = MainViewModel.Factory(repository, userId, userRole)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setupUI(savedInstanceState)
    }

    private fun setupUI(savedInstanceState: Bundle?) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // --- SEARCH LOGIC START ---
        val btnSearch = findViewById<ImageButton>(R.id.btnSearchDoctor)
        btnSearch.setOnClickListener {
            // TODO: Implement search functionality
            // For example, navigate to a SearchActivity or show a search dialog
            // val intent = Intent(this, SearchActivity::class.java)
            // startActivity(intent)
        }
        // --- SEARCH LOGIC END ---

        // --- LOGOUT LOGIC START ---
        val btnLogout = findViewById<ImageButton>(R.id.btnLogoutDoctor)
        btnLogout.setOnClickListener {
            performLogout()
        }
        // --- LOGOUT LOGIC END ---

        // Load Initial Fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DoctorHomeFragment())
                .commit()
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> DoctorHomeFragment()
                R.id.nav_appointments -> AppointmentsFragment.newInstance(userId, userRole)
                R.id.nav_messages -> MessagesFragment.newInstance(userId, userRole)
                R.id.nav_profile -> ProfileFragment.newInstance(userId, userRole)
                else -> null
            }

            if (fragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit()
                true
            } else {
                false
            }
        }
    }

    private fun performLogout() {
        // 1. Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // 2. Return to Login Screen (AuthActivity)
        val intent = Intent(this, AuthActivity::class.java)
        // Clear back stack so user can't press "Back" to return to dashboard
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
