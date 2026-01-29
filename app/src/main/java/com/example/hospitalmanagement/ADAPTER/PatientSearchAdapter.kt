package com.example.hospitalmanagement.ADAPTER

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.AppDatabase
import com.example.hospitalmanagement.HospitalRepository
import com.example.hospitalmanagement.Patient
import com.example.hospitalmanagement.ProfileOverlayDialog
import com.example.hospitalmanagement.R

class PatientSearchAdapter(
    private var patients: List<Patient>,
    private val onPatientClick: (Patient) -> Unit
) : RecyclerView.Adapter<PatientSearchAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPatientName)
        val tvDetails: TextView = view.findViewById(R.id.tvPatientDetails)
        val tvPhone: TextView = view.findViewById(R.id.tvPatientPhone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_patient_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val patient = patients[position]
        holder.tvName.text = patient.name
        holder.tvDetails.text = "Age: ${patient.age} • ${patient.gender}"
        holder.tvPhone.text = patient.phone

        holder.itemView.setOnClickListener {
            // Show Profile Overlay on click
            val context = holder.itemView.context
            if (context is AppCompatActivity) {
                // HACK: Quick access to repo for demo. In production, pass repository in constructor.
                val db = AppDatabase.getDatabase(context)
                val repo = HospitalRepository(db.doctorDao(), db.patientDao(), db.appointmentDao(), db.prescriptionDao(), db.messageDao(), db.consultationSessionDao(), db.aiExtractionDao(), db.medicalReportDao(), db.vitalSignsDao(), db.notificationDao(), db.emergencyContactDao(), db.medicationDao())

                val dialog = ProfileOverlayDialog(patient.patientId, "PATIENT", repo)
                dialog.show(context.supportFragmentManager, "ProfileOverlay")
            }
        }
    }

    override fun getItemCount() = patients.size

    fun updateData(newPatients: List<Patient>) {
        patients = newPatients
        notifyDataSetChanged()
    }
}
