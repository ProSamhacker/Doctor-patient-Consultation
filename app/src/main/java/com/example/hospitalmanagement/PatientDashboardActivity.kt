package com.example.hospitalmanagement

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.hospitalmanagement.FRAGMENTS.AppointmentsFragment
import com.example.hospitalmanagement.FRAGMENTS.MessagesFragment
import com.example.hospitalmanagement.FRAGMENTS.PatientHomeFragment
import com.example.hospitalmanagement.FRAGMENTS.ProfileFragment
import com.example.hospitalmanagement.auth.AuthActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PatientDashboardActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var repository: HospitalRepository
    private var userId: String = ""
    private var userRole: String = "PATIENT"

    // Voice & AI Components
    private var voiceService: VoiceRecognitionService? = null
    private var aiDialog: AlertDialog? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            showAiAssistantDialog()
        } else {
            Toast.makeText(this, "Microphone needed for voice features", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_dashboard)

        userId = intent.getStringExtra("USER_ID") ?: ""
        userRole = intent.getStringExtra("USER_ROLE") ?: "PATIENT"

        // Save FCM token so doctors can send cross-device push notifications
        if (userId.isNotEmpty()) {
            FcmTokenManager.saveTokenForUser(userId)
        }

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
            repository = viewModel.repository,
            userId = userId,
            userRole = "PATIENT"
        )
        setupUI(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppointmentScheduler.stopMonitoring()
    }



    private fun setupUI(savedInstanceState: Bundle?) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationViewPatient)
        val fabMic = findViewById<FloatingActionButton>(R.id.fabMicPatient)
        val containerId = R.id.fragment_container_patient

        // AppointmentScheduler.startMonitoring already handles notifications.
        // Removed duplicate polling loop here.

        findViewById<ImageButton>(R.id.btnSearchPatient).setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            intent.putExtra("USER_ID", userId)
            intent.putExtra("USER_ROLE", userRole)
            startActivity(intent)
        }

        // Notification Button Handler
        findViewById<ImageButton>(R.id.btnNotifications).setOnClickListener {
            Toast.makeText(this, "No new notifications", Toast.LENGTH_SHORT).show()
            // In a real implementation, you would open a NotificationFragment or Dialog here
        }

        findViewById<ImageButton>(R.id.btnLogoutPatient)?.setOnClickListener {
            performLogout()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(containerId, PatientHomeFragment())
                .commit()
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> PatientHomeFragment()
                R.id.nav_appointments -> AppointmentsFragment.newInstance(userId, userRole)
                R.id.nav_messages -> MessagesFragment.newInstance(userId, userRole)
                R.id.nav_profile -> ProfileFragment.newInstance(userId, userRole)
                else -> null
            }
            if (fragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(containerId, fragment)
                    .commit()
                true
            } else {
                false
            }
        }

        fabMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                showAiAssistantDialog()
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }

        fabMic.setOnLongClickListener {
            showEmergencyDialog()
            true
        }
    }

    private fun setupVoiceService() {
        // Obsolete — AI now uses full-screen AiAssistantActivity
    }

    fun showAiAssistantDialog() {
        val intent = Intent(this, AiAssistantActivity::class.java).apply {
            putExtra("USER_ID", userId)
            putExtra("USER_ROLE", userRole)
        }
        startActivity(intent)
    }

    private fun handleAiQuery(query: String) {
        // Obsolete — Handled in AiAssistantActivity
    }

    private fun performLogout() {
        // Clear FCM Token before logging out
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FcmTokenManager.clearTokenForUser(userId)
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut().addOnCompleteListener(this) {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, com.example.hospitalmanagement.auth.AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun showEmergencyDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚨 Emergency Mode")
            .setMessage("Do you need immediate assistance?")
            .setPositiveButton("Call Ambulance") { _, _ ->
                val intent = Intent(Intent.ACTION_DIAL, "tel:102".toUri())
                startActivity(intent)
            }
            .setNegativeButton("Contact Doctor") { _, _ -> contactDoctor() }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun contactDoctor() {
        lifecycleScope.launch {
            val appt = viewModel.allAppointments.value?.firstOrNull()
            if (appt != null) {
                val doctor = repository.getDoctor(appt.doctorId)
                if (doctor != null) {
                    startActivity(Intent(Intent.ACTION_DIAL, "tel:${doctor.phone}".toUri()))
                } else {
                    Toast.makeText(this@PatientDashboardActivity, "Doctor phone not found", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@PatientDashboardActivity, "No doctor assigned", Toast.LENGTH_SHORT).show()
            }
        }
    }
}