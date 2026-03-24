package com.example.hospitalmanagement

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ConsultationActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private var appointmentId: Int = 0
    private var userId: String = ""
    private var userRole: String = ""
    private var voiceService: VoiceRecognitionService? = null
    private var aiAssistant: GeminiConversationAssistant? = null

    // UI Elements
    private lateinit var tvTimer: TextView
    private lateinit var tvDoctorStatus: TextView
    private lateinit var tvPatientStatus: TextView
    private lateinit var tvTranscript: TextView
    private lateinit var tvPartialTranscript: TextView
    private lateinit var btnMic: ImageButton
    private lateinit var btnVideoCall: ImageButton

    // AI Insights UI
    private lateinit var layoutAiInsights: View
    private lateinit var tvSeverity: TextView
    private lateinit var tvSymptoms: TextView
    private lateinit var tvRedFlags: TextView
    private lateinit var tvQuestions: TextView
    private lateinit var tvDiagnosis: TextView
    private lateinit var layoutRedFlags: LinearLayout
    private lateinit var btnRefreshInsights: MaterialButton

    private val firestore = FirebaseFirestore.getInstance()
    private var waitTimer: CountDownTimer? = null
    private var durationTimer: CountDownTimer? = null // To show duration
    private var isMicOn = true
    private var otherPartyJoined = false
    private var sessionStartTime: Long = 0
    private var fullTranscript = StringBuilder()
    private var lastAiAnalysisLength = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consultation)

        appointmentId = intent.getIntExtra("APP_ID", 0)
        userId = intent.getStringExtra("USER_ID") ?: ""
        userRole = intent.getStringExtra("USER_ROLE") ?: ""

        if (appointmentId == 0) finish()

        val database = AppDatabase.getDatabase(this)
        val repository = HospitalRepository(
            database.doctorDao(), database.patientDao(), database.appointmentDao(),
            database.prescriptionDao(), database.messageDao(), database.consultationSessionDao(),
            database.aiExtractionDao(), database.medicalReportDao(), database.vitalSignsDao(),
            database.notificationDao(), database.emergencyContactDao(), database.medicationDao()
        )
        val factory = MainViewModel.Factory(repository, userId, userRole)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        try {
            aiAssistant = GeminiConversationAssistant(BuildConfig.GEMINI_API_KEY)
        } catch (e: Exception) {
            aiAssistant = null
        }

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
        tvPartialTranscript = findViewById(R.id.tvPartialTranscript)

        layoutAiInsights = findViewById(R.id.layoutAiInsights)
        tvSeverity = findViewById(R.id.tvSeverity)
        tvSymptoms = findViewById(R.id.tvSymptoms)
        tvRedFlags = findViewById(R.id.tvRedFlags)
        tvQuestions = findViewById(R.id.tvQuestions)
        tvDiagnosis = findViewById(R.id.tvDiagnosis)
        layoutRedFlags = findViewById(R.id.layoutRedFlags)
        btnRefreshInsights = findViewById(R.id.btnRefreshInsights)
        // Mic is hidden — transcription happens inside the Jitsi video call (VideoCallActivity)
        btnMic = findViewById(R.id.btnMicToggle)
        btnMic.visibility = View.GONE
        btnVideoCall = findViewById(R.id.btnVideoCall)

        findViewById<ImageButton>(R.id.btnEndCall).setOnClickListener { endMeeting() }

        btnVideoCall.setOnClickListener { launchVideoCall() }

        btnRefreshInsights.setOnClickListener { refreshAiInsights() }
    }

    private fun launchVideoCall() {
        lifecycleScope.launch {
            try {
                val intent = Intent(this@ConsultationActivity, VideoCallActivity::class.java).apply {
                    putExtra("APPOINTMENT_ID", appointmentId)
                    putExtra("USER_ROLE", userRole)
                    putExtra("USER_ID", userId)
                    putExtra("DOCTOR_NAME", "Doctor")
                    putExtra("PATIENT_NAME", "Patient")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this@ConsultationActivity, "Error launching video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun joinMeeting() {
        val statusField = if (userRole == "DOCTOR") "doctorJoined" else "patientJoined"
        val meetingRef = firestore.collection("appointments").document(appointmentId.toString())

        meetingRef.update(statusField, true).addOnFailureListener {
            val data = hashMapOf(statusField to true, "transcript" to "")
            meetingRef.set(data)
        }

        meetingRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener

            val isDoctorHere = snapshot.getBoolean("doctorJoined") == true
            val isPatientHere = snapshot.getBoolean("patientJoined") == true
            val remoteTranscript = snapshot.getString("transcript") ?: ""

            runOnUiThread {
                updateStatusUI(isDoctorHere, isPatientHere)
                tvTranscript.text = remoteTranscript
                findViewById<ScrollView>(R.id.scrollTranscript).fullScroll(ScrollView.FOCUS_DOWN)

                // BOTH CONNECTED LOGIC
                if (isDoctorHere && isPatientHere && !otherPartyJoined) {
                    otherPartyJoined = true
                    stopWaitTimer()
                    startDurationTimer() // Start counting duration
                    Toast.makeText(this, "Session Started", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateStatusUI(doctorHere: Boolean, patientHere: Boolean) {
        tvDoctorStatus.text = if (doctorHere) "Joined" else "Waiting..."
        tvDoctorStatus.setTextColor(if (doctorHere) 0xFF4CAF50.toInt() else 0xFFFFA000.toInt())
        tvPatientStatus.text = if (patientHere) "Joined" else "Waiting..."
        tvPatientStatus.setTextColor(if (patientHere) 0xFF4CAF50.toInt() else 0xFFFFA000.toInt())
    }

    private fun startWaitTimer() {
        waitTimer = object : CountDownTimer(300000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val min = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val sec = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                tvTimer.text = "Waiting: " + String.format("%02d:%02d", min, sec)
            }
            override fun onFinish() {
                if (!otherPartyJoined) {
                    Toast.makeText(this@ConsultationActivity, "Timed out waiting for other party", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()
    }

    private fun stopWaitTimer() {
        waitTimer?.cancel()
        sessionStartTime = System.currentTimeMillis()
    }

    private fun startDurationTimer() {
        // Simple UI timer for duration
        durationTimer = object : CountDownTimer(3600000, 1000) { // Up to 1 hour
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = System.currentTimeMillis() - sessionStartTime
                val min = TimeUnit.MILLISECONDS.toMinutes(elapsed)
                val sec = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
                tvTimer.text = String.format("%02d:%02d", min, sec)
                tvTimer.setTextColor(0xFF4CAF50.toInt())
            }
            override fun onFinish() {}
        }.start()
    }

    private fun setupVoiceService() {
        // Voice recording is disabled on the ConsultationActivity waiting screen.
        // The actual mic transcription runs inside VideoCallActivity alongside Jitsi.
        // Nothing to set up here.
    }

    private fun uploadTranscriptChunk(text: String) {
        val prefix = if (userRole == "DOCTOR") "Dr: " else "Pt: "
        val formattedText = "$prefix$text"
        fullTranscript.append("\n").append(formattedText)
        lifecycleScope.launch { viewModel.addToTranscript(formattedText) }
        val meetingRef = firestore.collection("appointments").document(appointmentId.toString())
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(meetingRef)
            val current = snapshot.getString("transcript") ?: ""
            transaction.update(meetingRef, "transcript", "$current\n$formattedText")
        }
    }

    private fun refreshAiInsights() {
        val transcript = fullTranscript.toString()
        if (transcript.length < 50) return
        btnRefreshInsights.isEnabled = false
        lifecycleScope.launch {
            val insights = aiAssistant?.getLiveInsights(transcript)
            runOnUiThread {
                btnRefreshInsights.isEnabled = true
                if (insights != null) {
                    updateAiInsightsUI(insights)
                    lastAiAnalysisLength = transcript.length
                }
            }
        }
    }

    private fun updateAiInsightsUI(insights: GeminiConversationAssistant.LiveInsights) {
        layoutAiInsights.visibility = View.VISIBLE
        tvSeverity.text = insights.severity
        tvSymptoms.text = if (insights.detectedSymptoms.isEmpty()) "None" else insights.detectedSymptoms.joinToString("\n") { "• $it" }
        if (insights.redFlags.isNotEmpty()) {
            layoutRedFlags.visibility = View.VISIBLE
            tvRedFlags.text = insights.redFlags.joinToString("\n") { "• $it" }
        } else {
            layoutRedFlags.visibility = View.GONE
        }
        tvQuestions.text = insights.suggestedQuestions.take(3).joinToString("\n") { "• $it" }
        tvDiagnosis.text = insights.preliminaryDiagnosis
    }

    private fun endMeeting() {
        if (userRole == "DOCTOR") {
            // Only doctor can officially "Close" the session logic in DB
            viewModel.endConsultation(fullTranscript.toString())
        }
        val statusField = if (userRole == "DOCTOR") "doctorJoined" else "patientJoined"
        firestore.collection("appointments").document(appointmentId.toString()).update(statusField, false)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceService?.shutdown()
        waitTimer?.cancel()
        durationTimer?.cancel()
        endMeeting()
    }
}