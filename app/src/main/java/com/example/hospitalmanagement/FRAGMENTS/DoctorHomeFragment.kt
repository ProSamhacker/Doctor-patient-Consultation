package com.example.hospitalmanagement.FRAGMENTS

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog // Added Import
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.hospitalmanagement.AppointmentStatus
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.MedicalExtractionResult // Ensure this data class is available
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
                    // Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
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
        viewModel.startConsultation(1) // Using dummy appointment ID
        currentSessionTranscript.clear()
        isRecordingSession = true
        
        updateMicButton(true)
        tvStatus?.text = "Recording Session..."
        tvLiveTranscript?.text = "Listening..."
        
        startListening()
    }

    private fun startListening() {
        if (isRecordingSession) {
            voiceService?.startListening()
            updateMicButton(true)
        }
    }

    private fun stopConsultationSession() {
        isRecordingSession = false
        voiceService?.stopListening()
        updateMicButton(false)
        tvStatus?.text = "Processing..."
        
        val transcript = currentSessionTranscript.toString()
        if (transcript.isNotBlank()) {
            processConsultation(transcript)
        } else {
            tvStatus?.text = "No content recorded"
        }
    }

    private fun handleVoiceResult(text: String) {
        activity?.runOnUiThread {
            if (currentSessionTranscript.isNotEmpty()) {
                currentSessionTranscript.append(" ")
            }
            currentSessionTranscript.append(text)
            
            updateTranscript(currentSessionTranscript.toString())
            viewModel.addToTranscript(text)
            
            if (isRecordingSession) {
                // Delay to restart listening (simulating continuous listening)
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
                tvStatus?.text = "AI Analyzing..."
                
                // Use ViewModel instead of direct Repository
                viewModel.extractMedicalInfo(transcript) { result ->
                    activity?.runOnUiThread {
                        showPrescriptionDialog(result)
                        tvStatus?.text = "Analysis Complete"
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    tvStatus?.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun showPrescriptionDialog(data: MedicalExtractionResult) {
        // Inflate the custom dialog layout
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_medication, null)
        
        val etDiagnosis = dialogView.findViewById<EditText>(R.id.etDiagnosis)
        val etMedication = dialogView.findViewById<EditText>(R.id.etMedicationName)
        // Check if these IDs exist in your dialog_add_medication.xml. 
        // If not, you might need to adjust the IDs here or in the XML.

        // Auto-fill fields from AI result
        etDiagnosis?.setText(data.diagnosis)
        // Take the first medication if available
        if (data.medications.isNotEmpty()) {
            etMedication?.setText(data.medications[0].name)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("🤖 AI Auto-Draft")
            .setView(dialogView)
            .setPositiveButton("Verify & Send") { _, _ ->
                // Save logic here (e.g., viewModel.createPrescription(...))
                Toast.makeText(context, "Prescription Sent!", Toast.LENGTH_SHORT).show()
                updateTranscript("Prescription sent for: ${data.diagnosis}")
            }
            .setNegativeButton("Edit", null)
            .show()
    }

    fun updateTranscript(text: String) {
        tvLiveTranscript?.text = text.ifBlank { "Tap mic to start..." }
    }

    private fun updateMicButton(isActive: Boolean) {
        if (isActive) {
            btnMic?.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
            btnMic?.setImageResource(R.drawable.ic_stop)
        } else {
            btnMic?.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white))
            btnMic?.setImageResource(R.drawable.ic_mic)
        }
    }

    private fun observeData() {
        viewModel.currentDoctor.observe(viewLifecycleOwner) { doctor ->
            doctor?.let { tvDoctorName?.text = it.name }
        }
        viewModel.upcomingAppointments.observe(viewLifecycleOwner) { appointments ->
            val count = appointments.count { it.status == AppointmentStatus.SCHEDULED }
            tvScheduleCount?.text = "$count Appointments Remaining"
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
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