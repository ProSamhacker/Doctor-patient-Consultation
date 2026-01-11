package com.example.hospitalmanagement

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.hospitalmanagement.FRAGMENTS.AppointmentsFragment
import com.example.hospitalmanagement.FRAGMENTS.DoctorHomeFragment
import com.example.hospitalmanagement.FRAGMENTS.MessagesFragment
import com.example.hospitalmanagement.FRAGMENTS.ProfileFragment
import com.example.hospitalmanagement.auth.AuthActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.launch

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
        AppointmentScheduler.startMonitoring(
            context = this,
            scope = lifecycleScope,
            repository = viewModel.repository, // Access repo via ViewModel or create new instance
            userId = userId,
            userRole = "DOCTOR"
        )

        setupUI(savedInstanceState)
    }
    override fun onDestroy() {
        super.onDestroy()
        AppointmentScheduler.stopMonitoring()
    }
    private fun setupUI(savedInstanceState: Bundle?) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        // Inside setupUI or onCreate of Dashboard
        lifecycleScope.launch {
            // Poll every minute
            while (true) {
                val now = System.currentTimeMillis()
                // Simple check: Look for accepted appointments +/- 1 minute from now
                viewModel.allAppointments.value?.find { appt ->
                    val diff = Math.abs(appt.dateTime - now)
                    // 60000ms = 1 minute tolerance
                    diff < 60000 && appt.status == AppointmentStatus.SCHEDULED
                }?.let { appointment ->
                    // Found a meeting starting NOW
                    AppointmentScheduler.triggerMeetingNotification(
                        this@DoctorDashboardActivity, // or PatientDashboardActivity
                        appointment.appId,
                        userId,
                        userRole
                    )
                    // Prevent spamming
                    kotlinx.coroutines.delay(60000)
                }
                kotlinx.coroutines.delay(30000) // Check every 30 seconds
            }
        }
        // --- SEARCH LOGIC START ---
        val btnSearch = findViewById<ImageButton>(R.id.btnSearchDoctor)
        btnSearch.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            intent.putExtra("USER_ID", userId)
            intent.putExtra("USER_ROLE", userRole)
            startActivity(intent)
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
        // 1. Configure Google Sign In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 2. Sign out of Google Client
        googleSignInClient.signOut().addOnCompleteListener(this) {
            // 3. Sign out of Firebase
            FirebaseAuth.getInstance().signOut()

            // 4. Navigate back to Auth
            val intent = Intent(this, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
