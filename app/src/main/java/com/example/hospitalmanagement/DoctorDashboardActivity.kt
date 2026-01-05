package com.example.hospitalmanagement

import android.content.Intent
import android.os.Bundle
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
        // LINKING TO XML: This line connects this class to your layout
        setContentView(R.layout.activity_doctor_dashboard)

        // Get Data passed from AuthActivity
        userId = intent.getStringExtra("USER_ID") ?: ""
        userRole = intent.getStringExtra("USER_ROLE") ?: "DOCTOR"

        setupDatabaseAndViewModel()
        setupUI(savedInstanceState)
    }

    private fun setupDatabaseAndViewModel() {
        val database = AppDatabase.getDatabase(this)
        repository = HospitalRepository(
            database.doctorDao(), database.patientDao(), database.appointmentDao(),
            database.prescriptionDao(), database.messageDao(), database.consultationSessionDao(),
            database.aiExtractionDao(), database.medicalReportDao(), database.vitalSignsDao(),
            database.notificationDao(), database.emergencyContactDao(), database.medicationDao()
        )
        val factory = MainViewModel.Factory(repository, userId, userRole)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
    }

    private fun setupUI(savedInstanceState: Bundle?) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Load the Initial Fragment (Home)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DoctorHomeFragment())
                .commit()
        }

        // Handle Bottom Navigation Clicks
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

        // Optional: Setup Logout if you added a button in your XML header
        // findViewById<ImageButton>(R.id.btnLogout)?.setOnClickListener { performLogout() }
    }

    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
