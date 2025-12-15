package com.example.hospitalmanagement.FRAGMENTS

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.R
import com.example.hospitalmanagement.VoiceRecognitionService
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.util.Locale

class PatientHomeFragment : Fragment(), TextToSpeech.OnInitListener {
    private lateinit var viewModel: MainViewModel
    private lateinit var tts: TextToSpeech
    private var voiceService: VoiceRecognitionService? = null
    
    // UI Elements
    private var btnReadPrescription: MaterialButton? = null
    private var fabMicPatient: FloatingActionButton? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_patient_home, container, false)

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        tts = TextToSpeech(requireContext(), this)
        
        // Initialize Views
        btnReadPrescription = view.findViewById(R.id.btnReadPrescription)
        fabMicPatient = view.findViewById(R.id.fabMicPatient)

        setupButtons()
        setupVoiceService()
        observeData()

        return view
    }
    
    private fun setupButtons() {
        // 1. Read Aloud Button
        btnReadPrescription?.setOnClickListener {
            // Hardcoded for the prototype demo
            val textToRead = "Your daily medications are: 8 AM, Amoxicillin 500mg, take with food. 2 PM, Paracetamol, take if fever persists."
            speakOut(textToRead)
        }

        // 2. Mic Button (Layman Translator)
        fabMicPatient?.setOnClickListener {
             openLaymanTranslator()
        }
    }

    private fun speakOut(text: String) {
        if (::tts.isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        } else {
            Toast.makeText(context, "Voice not ready yet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupVoiceService() {
        voiceService = VoiceRecognitionService(
            context = requireContext(),
            onResult = { text ->
                handleLaymanQuery(text)
            },
            onError = { error ->
                activity?.runOnUiThread {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // ✅ PUBLIC method that MainActivity can call
    fun openLaymanTranslator() {
        if (checkPermissions()) {
            showLaymanDialog()
        } else {
            requestPermissions()
        }
    }

    private fun showLaymanDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("🎤 Layman Translator")
            .setMessage("Ask me to explain any medical term.\n\nExample: 'What is hypertension?'")
            .setPositiveButton("Ask Now") { _, _ ->
                startListeningForQuery()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startListeningForQuery() {
        Toast.makeText(context, "Listening...", Toast.LENGTH_SHORT).show()
        voiceService?.startListening()
    }

    private fun handleLaymanQuery(query: String) {
        activity?.runOnUiThread {
            // Show loading dialog
            val loadingDialog = AlertDialog.Builder(requireContext())
                .setTitle("Thinking...")
                .setMessage("You asked: \"$query\"")
                .setCancelable(false)
                .create()
            
            loadingDialog.show()

            lifecycleScope.launch {
                try {
                    viewModel.getLaymanExplanation(query) { explanation ->
                        activity?.runOnUiThread {
                            loadingDialog.dismiss()
                            showExplanationDialog(query, explanation)
                            speakOut(explanation)
                        }
                    }
                } catch (e: Exception) {
                    activity?.runOnUiThread {
                        loadingDialog.dismiss()
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showExplanationDialog(query: String, explanation: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("💡 Simple Explanation")
            .setMessage("Q: $query\n\nA: $explanation")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun observeData() {
        viewModel.currentPatient.observe(viewLifecycleOwner) { patient ->
            // Update UI with patient name if needed
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        voiceService?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }
}