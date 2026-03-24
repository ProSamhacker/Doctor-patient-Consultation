package com.example.hospitalmanagement.FRAGMENTS

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.ADAPTER.MedicationTrackerItem
import com.example.hospitalmanagement.AppointmentStatus
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.R
import com.example.hospitalmanagement.SearchActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale

class PatientHomeFragment : Fragment(), TextToSpeech.OnInitListener {
    private lateinit var viewModel: MainViewModel
    private lateinit var tts: TextToSpeech
    private var medicationList: List<MedicationTrackerItem> = emptyList()

    private var btnReadMedication: MaterialButton? = null
    private var tvPatientName: TextView? = null // To reference the name view

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_patient_home, container, false)

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        tts = TextToSpeech(requireContext(), this)

        btnReadMedication = view.findViewById(R.id.btnReadPrescription)
        tvPatientName = view.findViewById(R.id.tvPatientName) // Initialize TextView

        setupButtons(view)
        setupRecyclerView(view)
        setupObservers(view)
        setupQuickActions(view)

        return view
    }

    private fun setupButtons(view: View) {
        btnReadMedication?.setOnClickListener {
            if (medicationList.isNotEmpty()) {
                val sb = StringBuilder("Your medications for today are: ")
                medicationList.forEach { med ->
                    sb.append("${med.schedule.medicationName}, ${med.schedule.dosage}. ")
                }
                speakOut(sb.toString())
            } else {
                speakOut("You have no medications scheduled for today.")
            }
        }
    }

    private fun setupQuickActions(view: View) {
        view.findViewById<View>(R.id.cardBookAppointment)?.setOnClickListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            intent.putExtra("USER_ID", viewModel.userId)
            intent.putExtra("USER_ROLE", "PATIENT")
            startActivity(intent)
        }

        view.findViewById<View>(R.id.cardMedicalRecords)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_patient, MedicalReportsFragment.newInstance(viewModel.userId))
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<View>(R.id.cardPrescriptions)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_patient, PrescriptionsFragment.newInstance(viewModel.userId))
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupRecyclerView(view: View) {
        val rvMedication = view.findViewById<RecyclerView>(R.id.rvMedicationSchedule)
        rvMedication.layoutManager = LinearLayoutManager(context)

        viewModel.todayMedications.observe(viewLifecycleOwner) { items ->
            medicationList = items ?: emptyList()

            if (medicationList.isNullOrEmpty()) {
                rvMedication.visibility = View.GONE
                view.findViewById<TextView>(R.id.tvNoMeds)?.visibility = View.VISIBLE
            } else {
                rvMedication.visibility = View.VISIBLE
                view.findViewById<TextView>(R.id.tvNoMeds)?.visibility = View.GONE
                rvMedication.adapter = com.example.hospitalmanagement.ADAPTER.MedicationAdapter(medicationList) { item ->
                    viewModel.markMedicationTaken(item)
                }
            }
        }
    }

    private fun setupObservers(view: View) {
        // Observer for Patient Name
        viewModel.currentPatient.observe(viewLifecycleOwner) { patient ->
            patient?.let {
                tvPatientName?.text = "Hi, ${it.name}"
            }
        }

        viewModel.allAppointments.observe(viewLifecycleOwner) { appointments ->
            val now = System.currentTimeMillis()
            val closestAppt = appointments
                ?.filter { it.status == AppointmentStatus.SCHEDULED && it.dateTime > now }
                ?.minByOrNull { it.dateTime }

            val tvDate = view.findViewById<TextView>(R.id.tvAppointmentDate)
            val tvTime = view.findViewById<TextView>(R.id.tvAppointmentTime)

            if (closestAppt != null) {
                val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                tvDate.text = dateFormat.format(closestAppt.dateTime)
                tvTime.text = "Doctor • ${timeFormat.format(closestAppt.dateTime)}"
            } else {
                tvDate.text = "No Appointment"
                tvTime.text = "Check back later"
            }
        }
    }

    private fun speakOut(text: String) {
        if (::tts.isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
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