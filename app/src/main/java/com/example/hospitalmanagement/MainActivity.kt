package com.example.hospitalmanagement

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var viewModel: MainViewModel
    private lateinit var repository: HospitalRepository
    private lateinit var tts: TextToSpeech
    private var userRole: String = "PATIENT"
    private var userId: String = ""
    private var currentAppointmentId: Int? = null
    private var isRecording = false
    private var recordedTranscript = StringBuilder()

    // Fragments
    private val doctorHomeFragment = DoctorHomeFragment()
    private val patientHomeFragment = PatientHomeFragment()
    private val appointmentsFragment = AppointmentsFragment()
    private val messagesFragment = MessagesFragment()
    private val profileFragment = ProfileFragment()

    // Voice recognition launcher
    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val spokenText = result.data!!
                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.get(0)

            if (!spokenText.isNullOrBlank()) {
                handleVoiceCommand(spokenText)
            }
        }
    }

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val callGranted = permissions[Manifest.permission.CALL_PHONE] ?: false

        if (!micGranted) {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get user role and ID from intent
        userRole = intent.getStringExtra("USER_ROLE") ?: "PATIENT"
        userId = intent.getStringExtra("USER_ID") ?: if (userRole == "DOCTOR") "DOC001" else "PAT001"

        // Initialize database and repository
        val database = AppDatabase.getDatabase(this)
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

        // Initialize ViewModel
        val factory = MainViewModel.Factory(repository, userId, userRole)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        // Initialize TTS
        tts = TextToSpeech(this, this)

        // Setup UI based on role
        setupUI()

        // Request permissions
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

        // Set initial fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, doctorHomeFragment)
            .commit()

        // Bottom navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, doctorHomeFragment)
                        .commit()
                    true
                }
                R.id.nav_appointments -> {
                    val fragment = AppointmentsFragment.newInstance(userId, userRole)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit()
                    true
                }
                R.id.nav_messages -> {
                    val fragment = MessagesFragment.newInstance(userId, userRole)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit()
                    true
                }
                R.id.nav_profile -> {
                    val fragment = ProfileFragment.newInstance(userId, userRole)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit()
                    true
                }
                else -> false
            }
        }

        // Voice button
        fabMic.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startVoiceRecognition()
            }
        }

        // Observe ViewModel
        observeDoctorData()
    }

    private fun setupPatientUI() {
        val fabMic = findViewById<FloatingActionButton>(R.id.fabMicPatient)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationViewPatient)

        // Set initial fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container_patient, patientHomeFragment)
            .commit()

        // Bottom navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container_patient, patientHomeFragment)
                        .commit()
                    true
                }
                R.id.nav_appointments -> {
                    val fragment = AppointmentsFragment.newInstance(userId, userRole)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container_patient, fragment)
                        .commit()
                    true
                }
                R.id.nav_messages -> {
                    val fragment = MessagesFragment.newInstance(userId, userRole)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container_patient, fragment)
                        .commit()
                    true
                }
                R.id.nav_profile -> {
                    val fragment = ProfileFragment.newInstance(userId, userRole)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container_patient, fragment)
                        .commit()
                    true
                }
                else -> false
            }
        }

        // Voice button with emergency detection
        fabMic.setOnLongClickListener {
            showEmergencyDialog()
            true
        }

        fabMic.setOnClickListener {
            startVoiceRecognition()
        }

        // Observe ViewModel
        observePatientData()
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (userRole == "DOCTOR") "Listening to consultation..." else "Ask me anything...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechLauncher.launch(intent)
    }

    private fun stopRecording() {
        isRecording = false
        if (recordedTranscript.isNotEmpty()) {
            processConsultation(recordedTranscript.toString())
            recordedTranscript.clear()
        }
    }

    private fun handleVoiceCommand(command: String) {
        if (userRole == "DOCTOR") {
            handleDoctorCommand(command)
        } else {
            handlePatientCommand(command)
        }
    }

    private fun handleDoctorCommand(command: String) {
        val lowerCmd = command.lowercase()

        // Check for emergency keywords
        if (lowerCmd.contains("emergency") || lowerCmd.contains("urgent") || lowerCmd.contains("critical")) {
            handleEmergency(command)
            return
        }

        // Check for prescription commands
        if (lowerCmd.contains("prescribe") || lowerCmd.contains("medication")) {
            processPrescription(command)
            return
        }

        // Check for appointment commands
        if (lowerCmd.contains("next patient") || lowerCmd.contains("call patient")) {
            viewModel.getNextPatient()
            return
        }

        // Record consultation
        if (isRecording || lowerCmd.contains("start consultation")) {
            isRecording = true
            recordedTranscript.append(command).append(" ")
            updateTranscript("Recording: $command")
            return
        }

        // Default: add to transcript
        updateTranscript("Processing: $command")
    }

    private fun handlePatientCommand(command: String) {
        val lowerCmd = command.lowercase()

        // Check if asking for medical explanation
        if (lowerCmd.contains("what") || lowerCmd.contains("mean") ||
            lowerCmd.contains("explain") || lowerCmd.contains("define")) {

            lifecycleScope.launch {
                try {
                    val explanation = repository.getLaymanExplanation(command)
                    speakOut(explanation)
                    showExplanationDialog("Medical Term Explanation", explanation)
                } catch (e: Exception) {
                    speakOut("Sorry, I couldn't explain that right now.")
                }
            }
            return
        }

        // Check for emergency
        if (lowerCmd.contains("emergency") || lowerCmd.contains("help") ||
            lowerCmd.contains("ambulance")) {
            showEmergencyDialog()
            return
        }

        // Default response
        speakOut("I heard: $command. How can I help you?")
    }

    private fun processPrescription(transcript: String) {
        lifecycleScope.launch {
            try {
                updateTranscript("Analyzing consultation...")

                val extraction = repository.extractMedicalInfo(transcript)

                // Create prescription
                currentAppointmentId?.let { appId ->
                    val medications = extraction.medications.map { med ->
                        MedicationSchedule(
                            medicationName = med.name,
                            dosage = med.dosage,
                            frequency = med.frequency,
                            duration = med.duration,
                            timing = med.timing,
                            instructions = med.instructions
                        )
                    }

                    val prescription = Prescription(
                        appId = appId,
                        diagnosis = extraction.diagnosis,
                        medications = medications,
                        labTests = extraction.labTests,
                        instructions = extraction.instructions,
                        followUpDate = extraction.followUpDays?.let {
                            System.currentTimeMillis() + (it * 24 * 60 * 60 * 1000L)
                        }
                    )

                    repository.createPrescription(prescription)

                    updateTranscript("✓ Prescription saved!\n\nDiagnosis: ${extraction.diagnosis}\nMedications: ${medications.size}")
                    Toast.makeText(this@MainActivity, "Prescription created successfully", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                updateTranscript("Error: ${e.localizedMessage}")
                Toast.makeText(this@MainActivity, "Failed to create prescription", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processConsultation(transcript: String) {
        lifecycleScope.launch {
            currentAppointmentId?.let { appId ->
                // End the session
                viewModel.endConsultation(transcript)

                // Process with AI
                processPrescription(transcript)
            }
        }
    }

    private fun handleEmergency(details: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Emergency Alert")
            .setMessage("Emergency detected in consultation. What would you like to do?")
            .setPositiveButton("Call Ambulance") { _, _ ->
                callEmergency("102") // Ambulance number in India
            }
            .setNegativeButton("Notify Hospital") { _, _ ->
                // Notify hospital staff
                lifecycleScope.launch {
                    repository.createNotification(
                        NotificationEntity(
                            userId = "ADMIN",
                            userType = "ADMIN",
                            title = "Emergency Alert",
                            message = "Emergency in consultation: $details",
                            type = NotificationType.EMERGENCY
                        )
                    )
                }
                Toast.makeText(this, "Hospital staff notified", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showEmergencyDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚨 Emergency Mode")
            .setMessage("This will alert your emergency contacts and call for help. Continue?")
            .setPositiveButton("Call Ambulance") { _, _ ->
                callEmergency("102")
            }
            .setNegativeButton("Contact Doctor") { _, _ ->
                // Get doctor's contact
                currentAppointmentId?.let { appId ->
                    lifecycleScope.launch {
                        val appointment = repository.getAppointment(appId)
                        appointment?.let { app ->
                            val doctor = repository.getDoctor(app.doctorId)
                            doctor?.let {
                                callNumber(it.phone)
                            }
                        }
                    }
                }
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
            Toast.makeText(this, "Call permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun callNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        startActivity(intent)
    }

    private fun updateTranscript(text: String) {
        if (userRole == "DOCTOR" && doctorHomeFragment.isVisible) {
            doctorHomeFragment.updateTranscript(text)
        }
    }

    private fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun showExplanationDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Got it", null)
            .setNeutralButton("Read Again") { _, _ ->
                speakOut(message)
            }
            .show()
    }

    private fun observeDoctorData() {
        viewModel.upcomingAppointments.observe(this) { appointments ->
            // Update UI with appointments
        }

        viewModel.currentPatient.observe(this) { patient ->
            patient?.let {
                speakOut("Next patient: ${it.name}")
            }
        }
    }

    private fun observePatientData() {
        viewModel.prescriptions.observe(this) { prescriptions ->
            // Update UI with prescriptions
        }

        viewModel.notifications.observe(this) { notifications ->
            // Show notifications
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        if (userRole == "PATIENT") {
            permissions.add(Manifest.permission.CALL_PHONE)
        }

        val needsPermission = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsPermission) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "TTS language not supported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}