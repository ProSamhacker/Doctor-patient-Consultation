package com.example.hospitalmanagement

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.hospitalmanagement.FRAGMENTS.AppointmentsFragment
import com.example.hospitalmanagement.FRAGMENTS.DoctorHomeFragment
import com.example.hospitalmanagement.FRAGMENTS.MessagesFragment
import com.example.hospitalmanagement.FRAGMENTS.PatientHomeFragment
import com.example.hospitalmanagement.FRAGMENTS.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var repository: HospitalRepository
    private var userRole: String = "PATIENT"
    private var userId: String = ""
    private var voiceService: VoiceRecognitionService? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!micGranted) {
            Toast.makeText(this, "Microphone permission is required for voice features", 
                Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userRole = intent.getStringExtra("USER_ROLE") ?: "PATIENT"
        userId = intent.getStringExtra("USER_ID") ?: if (userRole == "DOCTOR") "DOC001" else "PAT001"

        val database = AppDatabase.getDatabase(this)
        
        // NO CONTEXT parameter - BuildConfig is used instead
        repository = HospitalRepository(
            database.doctorDao(),
            database.patientDao(),
            database.appointmentDao(),
            database.prescriptionDao(),
            database.messageDao(),
            database.consultationSessionDao(),
            database.aiExtractionDao(),
            database.medicalReportDao(),
            database.vitalSignsDao(),
            database.notificationDao(),
            database.emergencyContactDao(),
            database.medicationDao()
        )

        val factory = MainViewModel.Factory(repository, userId, userRole)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        if (userRole == "DOCTOR") {
            setContentView(R.layout.activity_doctor_dashboard)
            setupDoctorUI()
        } else {
            setContentView(R.layout.activity_patient_dashboard)
            setupPatientUI()
        }
    }

    private fun setupDoctorUI() {
        val fabMic = findViewById<FloatingActionButton>(R.id.fabMic)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DoctorHomeFragment())
            .commit()

        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> DoctorHomeFragment()
                R.id.nav_appointments -> AppointmentsFragment.newInstance(userId, userRole)
                R.id.nav_messages -> MessagesFragment.newInstance(userId, userRole)
                R.id.nav_profile -> ProfileFragment.newInstance(userId, userRole)
                else -> return@setOnItemSelectedListener false
            }
            
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }

        fabMic.setOnClickListener {
            if (supportFragmentManager.findFragmentById(R.id.fragment_container) 
                !is DoctorHomeFragment) {
                bottomNav.selectedItemId = R.id.nav_home
            }
        }
    }

    private fun setupPatientUI() {
        val fabMic = findViewById<FloatingActionButton>(R.id.fabMicPatient)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationViewPatient)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container_patient, PatientHomeFragment())
            .commit()

        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> PatientHomeFragment()
                R.id.nav_appointments -> AppointmentsFragment.newInstance(userId, userRole)
                R.id.nav_messages -> MessagesFragment.newInstance(userId, userRole)
                R.id.nav_profile -> ProfileFragment.newInstance(userId, userRole)
                else -> return@setOnItemSelectedListener false
            }
            
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_patient, fragment)
                .commit()
            true
        }

        fabMic.setOnClickListener {
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container_patient)
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

    private fun showEmergencyDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚨 Emergency Mode")
            .setMessage("Do you need immediate assistance?")
            .setPositiveButton("Call Ambulance") { _, _ ->
                callEmergency("102")
            }
            .setNegativeButton("Contact Doctor") { _, _ ->
                contactDoctor()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun callEmergency(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            startActivity(intent)
        } else {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            startActivity(intent)
        }
    }

    private fun contactDoctor() {
        lifecycleScope.launch {
            try {
                val appointments = viewModel.allAppointments.value
                val latestAppointment = appointments?.firstOrNull()
                
                latestAppointment?.let { appointment ->
                    val doctor = repository.getDoctor(appointment.doctorId)
                    doctor?.let {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${it.phone}"))
                        startActivity(intent)
                    }
                } ?: run {
                    Toast.makeText(this@MainActivity, 
                        "No doctor assigned yet", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, 
                    "Failed to get doctor info", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        
        val needsPermission = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsPermission) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onDestroy() {
        voiceService?.shutdown()
        super.onDestroy()
    }
}