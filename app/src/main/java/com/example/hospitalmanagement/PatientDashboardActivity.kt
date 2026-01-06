package com.example.hospitalmanagement

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton // Import ImageButton
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
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class PatientDashboardActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var repository: HospitalRepository
    private var userId: String = ""
    private var userRole: String = "PATIENT"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == false) {
            Toast.makeText(this, "Microphone needed for voice features", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_dashboard)

        userId = intent.getStringExtra("USER_ID") ?: ""
        userRole = intent.getStringExtra("USER_ROLE") ?: "PATIENT"

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
        checkPermissions()
    }

    private fun setupUI(savedInstanceState: Bundle?) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationViewPatient)
        val fabMic = findViewById<FloatingActionButton>(R.id.fabMicPatient)
        val containerId = R.id.fragment_container_patient

        // --- SEARCH LOGIC START ---
        val btnSearch = findViewById<ImageButton>(R.id.btnSearchPatient)
        btnSearch.setOnClickListener {
            // TODO: Implement search functionality
        }
        // --- SEARCH LOGIC END ---

        // --- LOGOUT LOGIC START ---
        val btnLogout = findViewById<ImageButton>(R.id.btnLogoutPatient)
        btnLogout?.setOnClickListener {
            performLogout()
        }
        // --- LOGOUT LOGIC END ---

        if (bottomNav == null || fabMic == null) {
            Toast.makeText(this, "Error: Dashboard views not found.", Toast.LENGTH_LONG).show()
            return
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
            val fragment = supportFragmentManager.findFragmentById(containerId)
            if (fragment is PatientHomeFragment) {
                fragment.openLaymanTranslator()
            } else {
                bottomNav.selectedItemId = R.id.nav_home
            }
        }

        fabMic.setOnLongClickListener {
            showEmergencyDialog()
            true
        }
    }
    private fun performLogout() {
        // 1. Configure Google Sign In options (just to get the client)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 2. Sign out of Google Client FIRST
        googleSignInClient.signOut().addOnCompleteListener(this) {
            // 3. Then Sign out of Firebase
            FirebaseAuth.getInstance().signOut()

            // 4. Return to Login Screen
            val intent = Intent(this, AuthActivity::class.java)
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

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }
}