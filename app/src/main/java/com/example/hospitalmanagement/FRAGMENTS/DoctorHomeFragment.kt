package com.example.hospitalmanagement.FRAGMENTS

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import com.example.hospitalmanagement.AppointmentStatus
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.R
import java.util.Locale

class DoctorHomeFragment : Fragment() {
    private var tvLiveTranscript: TextView? = null
    private var tvDoctorName: TextView? = null
    private var tvScheduleCount: TextView? = null
    private var btnMic: ImageButton? = null
    private var tvStatus: TextView? = null

    private lateinit var viewModel: MainViewModel
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

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

        setupMic()
        observeData()

        return view
    }

    private fun setupMic() {
        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        } else {
            tvStatus?.text = "Speech Recognition not available"
            btnMic?.isEnabled = false
            return
        }

        btnMic?.setOnClickListener {
            if (checkPermission()) {
                if (!isListening) {
                    startListening()
                } else {
                    stopListening()
                }
            } else {
                requestPermission()
            }
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {
                tvStatus?.text = "Listening..."
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                tvStatus?.text = "Processing..."
            }
            override fun onError(error: Int) {
                isListening = false
                val errorMsg = when(error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    else -> "Error: $error"
                }
                tvStatus?.text = "$errorMsg. Tap to retry."
                updateMicIcon(false)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    viewModel.addToTranscript(text)

                    // Trigger AI Analysis automatically after speech
                    analyzeWithAI(text)
                }
                isListening = false
                updateMicIcon(false)
                tvStatus?.text = "Tap to Record"
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())

        speechRecognizer?.startListening(intent)
        isListening = true
        updateMicIcon(true)
        tvStatus?.text = "Listening..."

        // Clear previous session if needed
        if (tvLiveTranscript?.text.toString().contains("Tap the mic")) {
            viewModel.startConsultation(1) // Using dummy appointment ID 1 for now
        }
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        updateMicIcon(false)
    }

    private fun analyzeWithAI(text: String) {
        tvStatus?.text = "Consulting Gemini AI..."
        viewModel.extractMedicalInfo(text) { result ->
            // This runs when AI finishes
            activity?.runOnUiThread {
                Toast.makeText(context, "Diagnosis: ${result.diagnosis}", Toast.LENGTH_LONG).show()
                tvStatus?.text = "AI Analysis Complete"
            }
        }
    }

    private fun updateMicIcon(listening: Boolean) {
        if (listening) {
            btnMic?.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
        } else {
            btnMic?.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white))
        }
    }

    private fun observeData() {
        viewModel.currentDoctor.observe(viewLifecycleOwner) { doctor ->
            doctor?.let { tvDoctorName?.text = it.name }
        }

        viewModel.upcomingAppointments.observe(viewLifecycleOwner) { appointments ->
            val scheduledCount = appointments.count { it.status == AppointmentStatus.SCHEDULED }
            tvScheduleCount?.text = "$scheduledCount Appointments Remaining"
        }

        viewModel.consultationTranscript.observe(viewLifecycleOwner) { transcript ->
            if (transcript.isNotBlank()) {
                updateTranscript(transcript)
            }
        }
    }

    // ✅ ADDED THIS MISSING FUNCTION
    fun updateTranscript(text: String) {
        tvLiveTranscript?.text = if (text.isBlank()) {
            "Tap the mic below to start a consultation session..."
        } else {
            text
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}