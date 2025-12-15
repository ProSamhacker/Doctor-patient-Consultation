package com.example.hospitalmanagement.FRAGMENTS

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.ADAPTER.PrescriptionPatientAdapter
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.R
import com.example.hospitalmanagement.VoiceRecognitionService
import kotlinx.coroutines.launch
import java.util.Locale

class PatientHomeFragment : Fragment(), TextToSpeech.OnInitListener {
    private lateinit var viewModel: MainViewModel
    private lateinit var rvPrescriptions: RecyclerView
    private lateinit var adapter: PrescriptionPatientAdapter
    private lateinit var tts: TextToSpeech
    private var voiceService: VoiceRecognitionService? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_patient_home, container, false)

        rvPrescriptions = view.findViewById(R.id.rvPrescriptions)
        rvPrescriptions.layoutManager = LinearLayoutManager(requireContext())

        tts = TextToSpeech(requireContext(), this)
        adapter = PrescriptionPatientAdapter(emptyList(), tts)
        rvPrescriptions.adapter = adapter

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        setupVoiceService()
        observeData()

        return view
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
            .setMessage("Ask me to explain any medical term in simple language.\n\nFor example:\n• What does hypertension mean?\n• Explain diabetes\n• What is an MRI?")
            .setPositiveButton("Ask Now") { _, _ ->
                startListeningForQuery()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startListeningForQuery() {
        Toast.makeText(context, "Listening... Ask your question", Toast.LENGTH_SHORT).show()
        voiceService?.startListening()
    }

    private fun handleLaymanQuery(query: String) {
        activity?.runOnUiThread {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Processing...")
                .setMessage("You asked: \"$query\"\n\nGetting explanation...")
                .setCancelable(false)
                .create()
            
            dialog.show()

            lifecycleScope.launch {
                try {
                    viewModel.getLaymanExplanation(query) { explanation ->
                        activity?.runOnUiThread {
                            dialog.dismiss()
                            showExplanationDialog(query, explanation)
                            voiceService?.speak(explanation)
                        }
                    }
                } catch (e: Exception) {
                    activity?.runOnUiThread {
                        dialog.dismiss()
                        Toast.makeText(
                            context,
                            "Failed to get explanation: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun showExplanationDialog(query: String, explanation: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("💡 Simple Explanation")
            .setMessage("Question: $query\n\nAnswer: $explanation")
            .setPositiveButton("Got it", null)
            .setNeutralButton("Ask Another") { _, _ ->
                startListeningForQuery()
            }
            .setNegativeButton("Read Again") { _, _ ->
                voiceService?.speak(explanation)
            }
            .show()
    }

    private fun observeData() {
        viewModel.prescriptions.observe(viewLifecycleOwner) { prescriptions ->
            adapter.updateData(prescriptions)
        }

        viewModel.currentPatient.observe(viewLifecycleOwner) { patient ->
            patient?.let {
                view?.findViewById<TextView>(R.id.tvPatientName)?.text = "Hi, ${it.name}"
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
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