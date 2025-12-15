package com.example.hospitalmanagement.ADAPTER

import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.FRAGMENTS.MedicationScheduleAdapter
import com.example.hospitalmanagement.Prescription
import com.example.hospitalmanagement.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===== Prescription Adapter for Patients =====
class PrescriptionPatientAdapter(
    private var prescriptions: List<Prescription>,
    private val tts: TextToSpeech?
) : RecyclerView.Adapter<PrescriptionPatientAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDiagnosis: TextView = view.findViewById(R.id.tvDiagnosis)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val rvMedications: RecyclerView = view.findViewById(R.id.rvMedications)
        val btnPlayAll: ImageView = view.findViewById(R.id.btnPlayAll)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prescription_patient, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val prescription = prescriptions[position]

        holder.tvDiagnosis.text = prescription.diagnosis
        holder.tvDate.text = formatDate(prescription.createdAt)

        // Setup medications RecyclerView
        val medicationAdapter = MedicationScheduleAdapter(
            prescription.medications,
            tts
        )
        holder.rvMedications.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.rvMedications.adapter = medicationAdapter

        // Play all medications
        holder.btnPlayAll.setOnClickListener {
            val allText = prescription.medications.joinToString(". ") { med ->
                "Take ${med.medicationName}, ${med.dosage}, ${med.frequency}, ${med.timing}"
            }
            tts?.speak(allText, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun getItemCount() = prescriptions.size

    fun updateData(newPrescriptions: List<Prescription>) {
        prescriptions = newPrescriptions
        notifyDataSetChanged()
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
