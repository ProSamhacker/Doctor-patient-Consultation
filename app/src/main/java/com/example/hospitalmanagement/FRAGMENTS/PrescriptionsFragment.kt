package com.example.hospitalmanagement.FRAGMENTS

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hospitalmanagement.ADAPTER.PrescriptionPatientAdapter
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.databinding.FragmentPrescriptionsBinding
import java.util.Locale

class PrescriptionsFragment : Fragment(), TextToSpeech.OnInitListener {

    private lateinit var binding: FragmentPrescriptionsBinding
    private lateinit var viewModel: MainViewModel
    private var userId: String = ""
    private var tts: TextToSpeech? = null

    companion object {
        fun newInstance(userId: String): PrescriptionsFragment {
            val fragment = PrescriptionsFragment()
            val args = Bundle()
            args.putString("USER_ID", userId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = arguments?.getString("USER_ID") ?: ""

        // Initialize TextToSpeech
        tts = TextToSpeech(requireContext(), this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPrescriptionsBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        setupRecyclerView()

        return binding.root
    }

    private fun setupRecyclerView() {
        binding.rvPrescriptions.layoutManager = LinearLayoutManager(context)

        // Observe prescriptions from ViewModel
        viewModel.prescriptions.observe(viewLifecycleOwner) { prescriptions ->
            if (prescriptions.isNullOrEmpty()) {
                binding.tvNoData.visibility = View.VISIBLE
                binding.rvPrescriptions.visibility = View.GONE
            } else {
                binding.tvNoData.visibility = View.GONE
                binding.rvPrescriptions.visibility = View.VISIBLE

                // Pass TTS instance to the adapter
                val adapter = PrescriptionPatientAdapter(prescriptions, tts)
                binding.rvPrescriptions.adapter = adapter
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    override fun onDestroy() {
        // Shutdown TTS to release resources
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        super.onDestroy()
    }
}