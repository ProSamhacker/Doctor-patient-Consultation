package com.example.hospitalmanagement

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class ConsultationActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private var appointmentId: Int = 0
    private var userId: String = ""
    private var userRole: String = ""
    private var voiceService: VoiceRecognitionService? = null

    // UI Elements
    private lateinit var tvTimer: TextView
    private lateinit var tvDoctorStatus: TextView
    private lateinit var tvPatientStatus: TextView
    private lateinit var tvTranscript: TextView
    private lateinit var btnMic: ImageButton

    private val firestore = FirebaseFirestore.getInstance()
    private var waitTimer: CountDownTimer? = null
    private var isMicOn = true
    private var otherPartyJoined = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consultation)

        appointmentId = intent.getIntExtra("APP_ID", 0)
        userId = intent.getStringExtra("USER_ID") ?: ""
        userRole = intent.getStringExtra("USER_ROLE") ?: ""

        if (appointmentId == 0) finish()

        // Init ViewModel
        val database = AppDatabase.getDatabase(this)
        val repository = HospitalRepository(
            database.doctorDao(), database.patientDao(), database.appointmentDao(),
            database.prescriptionDao(), database.messageDao(), database.consultationSessionDao(),
            database.aiExtractionDao(), database.medicalReportDao(), database.vitalSignsDao(),
            database.notificationDao(), database.emergencyContactDao(), database.medicationDao()
        )
        val factory = MainViewModel.Factory(repository, userId, userRole)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setupUI()
        joinMeeting()
        startWaitTimer()
        setupVoiceService()
    }

    private fun setupUI() {
        tvTimer = findViewById(R.id.tvTimer)
        tvDoctorStatus = findViewById(R.id.tvDoctorStatus)
        tvPatientStatus = findViewById(R.id.tvPatientStatus)
        tvTranscript = findViewById(R.id.tvFullTranscript)
        btnMic = findViewById(R.id.btnMicToggle)

        findViewById<ImageButton>(R.id.btnEndCall).setOnClickListener {
            endMeeting()
        }

        btnMic.setOnClickListener {
            isMicOn = !isMicOn
            if (isMicOn) {
                voiceService?.startListening()
                btnMic.background.setTint(getColor(R.color.green)) // Assume green resource or hardcode
                btnMic.setImageResource(R.drawable.ic_mic)
            } else {
                voiceService?.stopListening()
                btnMic.background.setTint(getColor(android.R.color.darker_gray))
                btnMic.setImageResource(R.drawable.ic_mic) // Or ic_mic_off
            }
        }
    }

    private fun joinMeeting() {
        // 1. Update My Status in Firestore
        val statusField = if (userRole == "DOCTOR") "doctorJoined" else "patientJoined"
        val meetingRef = firestore.collection("appointments").document(appointmentId.toString())

        meetingRef.update(statusField, true)
            .addOnFailureListener {
                // Create doc if doesn't exist (first person joining)
                val data = hashMapOf(
                    statusField to true,
                    "transcript" to ""
                )
                meetingRef.set(data)
            }

        // 2. Listen for Other Party & Transcript
        meetingRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener

            val isDoctorHere = snapshot.getBoolean("doctorJoined") == true
            val isPatientHere = snapshot.getBoolean("patientJoined") == true
            val remoteTranscript = snapshot.getString("transcript") ?: ""

            runOnUiThread {
                updateStatusUI(isDoctorHere, isPatientHere)
                tvTranscript.text = remoteTranscript

                // Auto-scroll
                findViewById<ScrollView>(R.id.scrollTranscript).fullScroll(ScrollView.FOCUS_DOWN)

                if (isDoctorHere && isPatientHere && !otherPartyJoined) {
                    otherPartyJoined = true
                    stopWaitTimer()
                    Toast.makeText(this, "Both parties connected!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateStatusUI(doctorHere: Boolean, patientHere: Boolean) {
        tvDoctorStatus.text = if (doctorHere) "Joined (Online)" else "Waiting..."
        tvDoctorStatus.setTextColor(if (doctorHere) 0xFF4CAF50.toInt() else 0xFFFFA000.toInt())

        tvPatientStatus.text = if (patientHere) "Joined (Online)" else "Waiting..."
        tvPatientStatus.setTextColor(if (patientHere) 0xFF4CAF50.toInt() else 0xFFFFA000.toInt())
    }

    private fun startWaitTimer() {
        // 5 Minutes Countdown (300,000 ms)
        waitTimer = object : CountDownTimer(300000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val min = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val sec = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                tvTimer.text = String.format("%02d:%02d", min, sec)
            }

            override fun onFinish() {
                if (!otherPartyJoined) {
                    Toast.makeText(this@ConsultationActivity, "Meeting Cancelled - User absent", Toast.LENGTH_LONG).show()
                    // Update DB status to CANCELLED via ViewModel
                    viewModel.updateAppointmentStatus(appointmentId, AppointmentStatus.CANCELLED)
                    finish()
                }
            }
        }.start()
    }

    private fun stopWaitTimer() {
        waitTimer?.cancel()
        tvTimer.text = "LIVE"
        tvTimer.setTextColor(0xFF4CAF50.toInt())
    }

    private fun setupVoiceService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        voiceService = VoiceRecognitionService(this, { text ->
            // Local speech detected -> Append to UI & Sync to Firestore
            uploadTranscriptChunk(text)
        }, { error ->
            // Handle error
        })
        voiceService?.startListening()
    }

    private fun uploadTranscriptChunk(text: String) {
        // In a real app, you append. Here we just simple-append to the field for demo
        val meetingRef = firestore.collection("appointments").document(appointmentId.toString())

        // Use a transaction or array-union in production. Here simplified:
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(meetingRef)
            val current = snapshot.getString("transcript") ?: ""
            val prefix = if (userRole == "DOCTOR") "Dr: " else "Pt: "
            val newText = "$current\n$prefix$text"
            transaction.update(meetingRef, "transcript", newText)
        }
    }

    private fun endMeeting() {
        // Update my status to false
        val statusField = if (userRole == "DOCTOR") "doctorJoined" else "patientJoined"
        firestore.collection("appointments").document(appointmentId.toString())
            .update(statusField, false)

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceService?.shutdown()
        waitTimer?.cancel()
        // Ensure we mark as left if we crash/close
        endMeeting()
    }
}