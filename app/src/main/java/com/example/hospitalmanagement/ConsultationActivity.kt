package com.example.hospitalmanagement

import android.Manifest
import android.content.pm.PackageManager
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

    // Ensure you have GeminiConversationAssistant.kt created, or comment this out
    private var aiAssistant: GeminiConversationAssistant? = null

    // UI Elements
    private lateinit var tvTimer: TextView
    private lateinit var tvDoctorStatus: TextView
    private lateinit var tvPatientStatus: TextView
    private lateinit var tvTranscript: TextView
    private lateinit var tvPartialTranscript: TextView
    private lateinit var btnMic: ImageButton

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
    private var isMicOn = true
    private var otherPartyJoined = false
    private var fullTranscript = StringBuilder()
    private var lastAiAnalysisLength = 0

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

        // Initialize AI Assistant
        try {
            aiAssistant = GeminiConversationAssistant(BuildConfig.GEMINI_API_KEY)
        } catch (e: Exception) {
            // Handle if class is missing or key issue
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

        // AI Insights
        layoutAiInsights = findViewById(R.id.layoutAiInsights)
        tvSeverity = findViewById(R.id.tvSeverity)
        tvSymptoms = findViewById(R.id.tvSymptoms)
        tvRedFlags = findViewById(R.id.tvRedFlags)
        tvQuestions = findViewById(R.id.tvQuestions)
        tvDiagnosis = findViewById(R.id.tvDiagnosis)
        layoutRedFlags = findViewById(R.id.layoutRedFlags)
        btnRefreshInsights = findViewById(R.id.btnRefreshInsights)
        btnMic = findViewById(R.id.btnMicToggle)

        findViewById<ImageButton>(R.id.btnEndCall).setOnClickListener {
            endMeeting()
        }

        btnMic.setOnClickListener {
            isMicOn = !isMicOn
            if (isMicOn) {
                // FIXED: Now VoiceRecognitionService accepts continuous param
                voiceService?.startListening(continuous = true)
                btnMic.setImageResource(R.drawable.ic_mic)
                btnMic.setColorFilter(0xFF4CAF50.toInt())
            } else {
                voiceService?.stopListening()
                btnMic.setImageResource(R.drawable.ic_mic_off)
                btnMic.setColorFilter(0xFF9E9E9E.toInt())
            }
        }

        btnRefreshInsights.setOnClickListener {
            refreshAiInsights()
        }
    }

    private fun joinMeeting() {
        val statusField = if (userRole == "DOCTOR") "doctorJoined" else "patientJoined"
        val meetingRef = firestore.collection("appointments").document(appointmentId.toString())

        meetingRef.update(statusField, true)
            .addOnFailureListener {
                val data = hashMapOf(
                    statusField to true,
                    "transcript" to ""
                )
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
        waitTimer = object : CountDownTimer(300000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val min = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val sec = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                tvTimer.text = String.format("%02d:%02d", min, sec)
            }

            override fun onFinish() {
                if (!otherPartyJoined) {
                    Toast.makeText(this@ConsultationActivity, "Meeting Cancelled - User absent", Toast.LENGTH_LONG).show()
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

        // FIXED: Explicit type for partial result to satisfy compiler
        voiceService = VoiceRecognitionService(
            context = this,
            onResult = { text ->
                runOnUiThread {
                    uploadTranscriptChunk(text)
                    tvPartialTranscript.text = ""

                    if (fullTranscript.length - lastAiAnalysisLength > 100) {
                        refreshAiInsights()
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    if (!error.contains("detect") && !error.contains("Network")) {
                        // Suppress minor errors in loop
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onPartialResult = { partial ->
                runOnUiThread {
                    tvPartialTranscript.text = "$partial..."
                }
            }
        )

        voiceService?.startListening(continuous = true)
    }

    private fun uploadTranscriptChunk(text: String) {
        val prefix = if (userRole == "DOCTOR") "Dr: " else "Pt: "
        val formattedText = "$prefix$text"
        fullTranscript.append("\n").append(formattedText)

        lifecycleScope.launch {
            viewModel.addToTranscript(formattedText)
        }

        val meetingRef = firestore.collection("appointments").document(appointmentId.toString())
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(meetingRef)
            val current = snapshot.getString("transcript") ?: ""
            val newText = "$current\n$formattedText"
            transaction.update(meetingRef, "transcript", newText)
        }
    }

    private fun refreshAiInsights() {
        val transcript = fullTranscript.toString()
        if (transcript.length < 50) return

        btnRefreshInsights.isEnabled = false
        lifecycleScope.launch {
            // Using aiAssistant if available, or fall back to Repository if you moved logic there
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
        val severityColor = when (insights.severity) {
            "LOW" -> 0xFF4CAF50.toInt()
            "NORMAL" -> 0xFF2196F3.toInt()
            "HIGH" -> 0xFFFFA000.toInt()
            "CRITICAL" -> 0xFFF44336.toInt()
            else -> 0xFF9E9E9E.toInt()
        }
        tvSeverity.background.setTint(severityColor)

        tvSymptoms.text = if (insights.detectedSymptoms.isEmpty()) "No symptoms detected yet" else insights.detectedSymptoms.joinToString("\n") { "• $it" }

        if (insights.redFlags.isNotEmpty()) {
            layoutRedFlags.visibility = View.VISIBLE
            tvRedFlags.text = insights.redFlags.joinToString("\n") { "• $it" }
        } else {
            layoutRedFlags.visibility = View.GONE
        }

        tvQuestions.text = if (insights.suggestedQuestions.isEmpty()) "Ask about symptom duration" else insights.suggestedQuestions.take(3).joinToString("\n") { "• $it" }
        tvDiagnosis.text = insights.preliminaryDiagnosis.ifBlank { "Assessing..." }
    }


    private fun endMeeting() {
        val statusField = if (userRole == "DOCTOR") "doctorJoined" else "patientJoined"
        firestore.collection("appointments").document(appointmentId.toString())
            .update(statusField, false)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceService?.shutdown()
        waitTimer?.cancel()
        endMeeting()
    }
}