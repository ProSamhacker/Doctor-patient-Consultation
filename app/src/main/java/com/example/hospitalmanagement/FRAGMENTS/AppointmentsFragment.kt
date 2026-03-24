package com.example.hospitalmanagement.FRAGMENTS

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.ADAPTER.AppointmentAdapter
import com.example.hospitalmanagement.Appointment
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.ProfileOverlayDialog
import com.example.hospitalmanagement.R
import com.example.hospitalmanagement.ConsultationActivity
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class AppointmentsFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: AppointmentAdapter
    private var userId: String = ""
    private var userRole: String = "PATIENT"

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
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        setupRecyclerView(view)
        return view
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvAppointments)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyState)

        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = AppointmentAdapter(
                appointments = emptyList(),
                userRole = userRole,
                onCallClick = { appointment -> handleCallClick(appointment) },
                onPrescribeClick = { /* handled via onViewPrescriptionClick */ },
                onRecordVitalsClick = { appointment ->
                    val dialog = com.example.hospitalmanagement.VitalSignsDialog.newInstance(
                            appointment.appId,
                            appointment.patientId
                    )
                    dialog.show(parentFragmentManager, "VitalSigns")
                },
                onProfileClick = { targetId, targetRole ->
                    val dialog = ProfileOverlayDialog(targetId, targetRole, viewModel.repository)
                    dialog.show(parentFragmentManager, "ProfileOverlay")
                },
                onAcceptClick = { appointment ->
                    viewModel.acceptAppointment(appointment, requireContext())
                    Toast.makeText(context, "✅ Appointment Accepted", Toast.LENGTH_SHORT).show()
                },
                onRejectClick = { appointment ->
                    viewModel.rejectAppointment(appointment, requireContext())
                    Toast.makeText(context, "❌ Appointment Rejected", Toast.LENGTH_SHORT).show()
                },
                onViewPrescriptionClick = { appointment ->
                    handleViewPrescriptionClick(appointment)
                }
        )

        recyclerView.adapter = adapter

        // Room Flow → LiveData → UI update. Because Room is the source of truth and
        // MainViewModel's Firestore listener keeps Room in sync, this auto-refreshes
        // within seconds of any remote change — no app restart needed.
        viewModel.allAppointments.observe(viewLifecycleOwner) { appointments ->
            if (appointments.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapter.updateData(appointments)
            }
        }

        viewModel.prescriptions.observe(viewLifecycleOwner) { prescriptions ->
            val map = prescriptions.associate { it.appId to true }
            adapter.updatePrescriptionMap(map)
        }
    }

    private fun handleCallClick(appointment: Appointment) {
        lifecycleScope.launch {
            try {
                // Now we launch the unified AI Consultation Dashboard instead of a raw Jitsi window
                val intent = Intent(requireContext(), ConsultationActivity::class.java).apply {
                    putExtra("APP_ID", appointment.appId)
                    putExtra("USER_ROLE", userRole)
                    putExtra("USER_ID", userId)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Error launching consultation: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleViewPrescriptionClick(appointment: Appointment) {
        lifecycleScope.launch {
            val prescription = viewModel.repository.getPrescription(appointment.appId)
            if (prescription != null) {
                val medsList = prescription.medications.joinToString("\n") {
                    "- ${it.medicationName} (${it.dosage})"
                }
                AlertDialog.Builder(requireContext())
                        .setTitle("Prescription")
                        .setMessage("💊 Diagnosis: ${prescription.diagnosis}\n\n📋 Medications:\n$medsList\n\n📝 Instructions: ${prescription.instructions}")
                        .setPositiveButton("Close", null)
                        .show()
            } else {
                Toast.makeText(context, "No prescription found.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
