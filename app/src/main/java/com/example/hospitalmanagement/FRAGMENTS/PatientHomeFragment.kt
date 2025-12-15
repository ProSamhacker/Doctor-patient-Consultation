package com.example.hospitalmanagement.FRAGMENTS

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.ADAPTER.PrescriptionPatientAdapter
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.R
import java.util.Locale
import kotlin.collections.get
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider

class PatientHomeFragment : Fragment(), TextToSpeech.OnInitListener {
    private lateinit var viewModel: MainViewModel
    private lateinit var rvPrescriptions: RecyclerView
    private lateinit var adapter: PrescriptionPatientAdapter
    private lateinit var tts: TextToSpeech

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

        observeData()

        return view
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

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}