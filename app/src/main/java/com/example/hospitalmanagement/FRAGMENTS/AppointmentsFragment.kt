package com.example.hospitalmanagement.FRAGMENTS

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.ADAPTER.AppointmentAdapter
import com.example.hospitalmanagement.Appointment
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.MedicationSchedule
import com.example.hospitalmanagement.Prescription
import com.example.hospitalmanagement.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppointmentsFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var rvAppointments: RecyclerView
    private lateinit var adapter: AppointmentAdapter
    private var userRole: String = "PATIENT"
    private var userId: String = ""

    companion object {
        fun newInstance(userId: String, userRole: String): AppointmentsFragment {
            val fragment = AppointmentsFragment()
            val args = Bundle()
            args.putString("USER_ID", userId)
            args.putString("USER_ROLE", userRole)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userId = it.getString("USER_ID", "")
            userRole = it.getString("USER_ROLE", "PATIENT")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_appointments, container, false)

        rvAppointments = view.findViewById(R.id.rvAppointments)
        rvAppointments.layoutManager = LinearLayoutManager(requireContext())

        adapter = AppointmentAdapter(
            appointments = emptyList(),
            userRole = userRole,
            onCallClick = { appointment -> handleCallClick(appointment) },
            onPrescribeClick = { appointment -> handlePrescribeClick(appointment) },
            onViewClick = { appointment -> handleViewClick(appointment) }
        )
        rvAppointments.adapter = adapter

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        observeData()

        return view
    }

    private fun observeData() {
        // Load appropriate appointments based on role
        if (userRole == "DOCTOR") {
            viewModel.loadDoctorAppointments(userId)
            viewModel.doctorAppointments.observe(viewLifecycleOwner) { appointments ->
                adapter.updateData(appointments)
            }
        } else {
            viewModel.loadPatientAppointments(userId)
            viewModel.patientAppointments.observe(viewLifecycleOwner) { appointments ->
                adapter.updateData(appointments)
            }
        }
    }

    // --- 1. CALL FUNCTIONALITY ---
    private fun handleCallClick(appointment: Appointment) {
        lifecycleScope.launch {
            // Determine who to call based on current user role
            val phone = if (userRole == "DOCTOR") {
                viewModel.repository.getPatient(appointment.patientId)?.phone
            } else {
                viewModel.repository.getDoctor(appointment.doctorId)?.phone
            }

            if (!phone.isNullOrBlank()) {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:$phone")
                startActivity(intent)
            } else {
                Toast.makeText(context, "Phone number not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- 2. PRESCRIBE FUNCTIONALITY ---
    private fun handlePrescribeClick(appointment: Appointment) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_medication, null)

        // Find views in the dialog layout
        val etDiagnosis = dialogView.findViewById<EditText>(R.id.etDiagnosis)
        val etMedication = dialogView.findViewById<EditText>(R.id.etMedicationName)
        // Ensure you have fields for dosage/instructions in your XML, or simplify for this example

        // Pre-fill diagnosis if available from appointment notes
        etDiagnosis.setText(appointment.chiefComplaint)

        AlertDialog.Builder(requireContext())
            .setTitle("Write Prescription")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val diagnosis = etDiagnosis.text.toString()
                val medicationName = etMedication.text.toString()

                if (diagnosis.isNotBlank() && medicationName.isNotBlank()) {
                    savePrescription(appointment, diagnosis, medicationName)
                } else {
                    Toast.makeText(context, "Please fill required details", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePrescription(appointment: Appointment, diagnosis: String, medName: String) {
        lifecycleScope.launch {
            try {
                // Create a basic medication schedule object
                val medication = MedicationSchedule(
                    medicationName = medName,
                    dosage = "1 tablet", // Default or add input field for this
                    frequency = "Twice Daily",
                    duration = "5 Days",
                    timing = "After Food"
                )

                val prescription = Prescription(
                    appId = appointment.appId,
                    diagnosis = diagnosis,
                    medications = listOf(medication),
                    instructions = "Take rest and drink plenty of water."
                )

                viewModel.createPrescription(prescription)
                Toast.makeText(context, "Prescription sent successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- 3. VIEW DETAILS FUNCTIONALITY ---
    private fun handleViewClick(appointment: Appointment) {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(appointment.dateTime))

        val message = """
            📅 Date: $dateStr
            🆔 Token: ${appointment.tokenNumber}
            📝 Complaint: ${appointment.chiefComplaint}
            📌 Status: ${appointment.status}
            📋 Type: ${appointment.type}
            ⏱ Duration: ${appointment.estimatedDuration} mins
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Appointment Details")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }
}