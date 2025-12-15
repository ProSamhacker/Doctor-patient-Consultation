package com.example.hospitalmanagement.FRAGMENTS

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.hospitalmanagement.AppointmentStatus
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.R
import com.example.hospitalmanagement.VoiceRecognitionService
import kotlinx.coroutines.launch

class DoctorHomeFragment : Fragment() {
    private var tvLiveTranscript: TextView? = null
    private var tvDoctorName: TextView? = null
    private var tvScheduleCount: TextView? = null
    private var btnMic: ImageButton? = null
    private var tvStatus: TextView? = null

    private lateinit var viewModel: MainViewModel
    private var voiceService: VoiceRecognitionService? = null
    private var currentSessionTranscript = StringBuilder()
    private var isRecordingSession = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_doctor_home, container, false)

        // Initialize Views
        tvLiveTranscript = view.findViewById(R.id.tvLiveTranscript)
        tvDoctorName = view.findViewById(R.id.tvDoctorName)
        tvScheduleCount = view.findViewById(R.id.tvScheduleCount)
        btnMic = view.findViewById(R.id.btnMic)
        tvStatus = view.findViewById(R.id.tvStatus)

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        setupVoiceService()
        setupMicButton()
        observeData()

        return view
    }

    private fun setupVoiceService() {
        voiceService = VoiceRecognitionService(
            context = requireContext(),
            onResult = { text ->
                handleVoiceResult(text)
            },
            onError = { error ->
                activity?.runOnUiThread {
                    tvStatus?.text = error
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    updateMicButton(false)
                }
            }
        )
    }

    private fun setupMicButton() {
        btnMic?.setOnClickListener {
            if (checkPermissions()) {
                toggleRecording()
            } else {
                requestPermissions()
            }
        }
    }

    private fun toggleRecording() {
        if (isRecordingSession) {
            stopConsultationSession()
        } else {
            startConsultationSession()
        }
    }

    private fun startConsultationSession() {
        // Start a new consultation session
        viewModel.startConsultation(1) // Using dummy appointment ID
        currentSessionTranscript.clear()
        isRecordingSession = true
        
        updateMicButton(true)
        tvStatus?.text = "Recording Session - Tap to continue"
        tvLiveTranscript?.text = "Session started. Speak naturally during the consultation..."
        
        // Start listening
        startListening()
    }

    private fun startListening() {
        if (isRecordingSession) {
            voiceService?.startListening()
            tvStatus?.text = "Listening..."
            updateMicButton(true)
        }
    }

    private fun stopConsultationSession() {
        isRecordingSession = false
        voiceService?.stopListening()
        updateMicButton(false)
        tvStatus?.text = "Processing consultation..."
        
        val transcript = currentSessionTranscript.toString()
        if (transcript.isNotBlank()) {
            processConsultation(transcript)
        } else {
            tvStatus?.text = "No content recorded"
            tvLiveTranscript?.text = "Tap the mic to start a new session..."
        }
    }

    private fun handleVoiceResult(text: String) {
        activity?.runOnUiThread {
            // Add to transcript
            if (currentSessionTranscript.isNotEmpty()) {
                currentSessionTranscript.append(" ")
            }
            currentSessionTranscript.append(text)
            
            // Update UI
            updateTranscript(currentSessionTranscript.toString())
            viewModel.addToTranscript(text)
            
            // Continue listening if session is active
            if (isRecordingSession) {
                // Small delay before next listening cycle
                btnMic?.postDelayed({
                    if (isRecordingSession) {
                        startListening()
                    }
                }, 500)
            }
        }
    }

    private fun processConsultation(transcript: String) {
        lifecycleScope.launch {
            try {
                tvStatus?.text = "Analyzing with AI..."
                
                viewModel.extractMedicalInfo(transcript) { result ->
                    activity?.runOnUiThread {
                        val summary = buildString {
                            append("✅ Consultation Analysis Complete\n\n")
                            append("Symptoms: ${result.symptoms}\n\n")
                            append("Diagnosis: ${result.diagnosis}\n\n")
                            append("Severity: ${result.severity}\n\n")
                            append("Medications: ${result.medications.size} prescribed\n\n")
                            if (result.labTests.isNotEmpty()) {
                                append("Tests: ${result.labTests.joinToString(", ")}\n\n")
                            }
                            append("Instructions: ${result.instructions}")
                        }
                        
                        updateTranscript(summary)
                        tvStatus?.text = "Tap mic to start new session"
                        
                        // Show success message
                        Toast.makeText(
                            context,
                            "Consultation processed successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Speak summary
                        voiceService?.speak("Consultation analysis complete. ${result.diagnosis}")
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    tvStatus?.text = "Error: ${e.message}"
                    Toast.makeText(context, "Failed to process", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateTranscript(text: String) {
        tvLiveTranscript?.text = if (text.isBlank()) {
            "Tap the mic below to start a consultation session..."
        } else {
            text
        }
    }

    private fun updateMicButton(isActive: Boolean) {
        if (isActive) {
            btnMic?.setColorFilter(
                ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
            )
            btnMic?.setImageResource(R.drawable.ic_stop)
        } else {
            btnMic?.setColorFilter(
                ContextCompat.getColor(requireContext(), android.R.color.white)
            )
            btnMic?.setImageResource(R.drawable.ic_mic)
        }
    }

    private fun observeData() {
        viewModel.currentDoctor.observe(viewLifecycleOwner) { doctor ->
            doctor?.let { 
                tvDoctorName?.text = it.name 
            }
        }

        viewModel.upcomingAppointments.observe(viewLifecycleOwner) { appointments ->
            val scheduledCount = appointments.count { 
                it.status == AppointmentStatus.SCHEDULED 
            }
            tvScheduleCount?.text = "$scheduledCount Appointments Remaining"
        }

        viewModel.consultationTranscript.observe(viewLifecycleOwner) { transcript ->
            if (transcript.isNotBlank() && !isRecordingSession) {
                updateTranscript(transcript)
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        voiceService?.shutdown()
        voiceService = null
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}